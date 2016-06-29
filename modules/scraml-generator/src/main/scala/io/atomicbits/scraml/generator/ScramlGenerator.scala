/*
 *
 *  (C) Copyright 2015 Atomic BITS (http://atomicbits.io).
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the GNU Affero General Public License
 *  (AGPL) version 3.0 which accompanies this distribution, and is available in
 *  the LICENSE file or at http://www.gnu.org/licenses/agpl-3.0.en.html
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Affero General Public License for more details.
 *
 *  Contributors:
 *      Peter Rigole
 *
 */

package io.atomicbits.scraml.generator

import java.io.File

import io.atomicbits.scraml.generator.codegen.{CaseClassGenerator, JavaResourceClassGenerator, PojoGenerator, ScalaResourceClassGenerator}
import io.atomicbits.scraml.generator.formatting.JavaFormatter
import io.atomicbits.scraml.generator.model._
import ClassRep.ClassMap
import io.atomicbits.scraml.generator.lookup.{SchemaLookup, SchemaLookupParser}
import io.atomicbits.scraml.jsonschemaparser.model.Schema
import io.atomicbits.scraml.jsonschemaparser.JsonSchemaParser
import org.raml.parser.rule.ValidationResult
import io.atomicbits.scraml.parser._
import io.atomicbits.scraml.parser.model._

import scala.collection.JavaConversions.mapAsJavaMap
import scala.language.postfixOps
import java.util.{Map => JMap}

import io.atomicbits.scraml.generator.license.{LicenseData, LicenseVerifier}

import scala.util.Try
import scalariform.formatter.ScalaFormatter
import scalariform.formatter.preferences._

/**
  * The main Scraml generator class.
  * This class is thread-safe and may be used by mutiple threads simultaneously.
  */
object ScramlGenerator {


  def generateScalaCode(ramlApiPath: String,
                        apiPackageName: String,
                        apiClassName: String,
                        licenseKey: Option[String],
                        thirdPartyLicenseHeader: Option[String]): JMap[String, String] =
    generateFor(Scala, ramlApiPath, apiPackageName, apiClassName, licenseKey, thirdPartyLicenseHeader)


  def generateJavaCode(ramlApiPath: String,
                       apiPackageName: String,
                       apiClassName: String,
                       licenseKey: Option[String],
                       thirdPartyLicenseHeader: Option[String]): JMap[String, String] =
    generateFor(Java, ramlApiPath, apiPackageName, apiClassName, licenseKey, thirdPartyLicenseHeader)


  private[generator] def generateFor(language: Language,
                                     ramlApiPath: String,
                                     apiPackageName: String,
                                     apiClassName: String,
                                     licenseKey: Option[String],
                                     thirdPartyLicenseHeader: Option[String]): JMap[String, String] = {

    println(s"Generating $language client.")

    val licenseData: Option[LicenseData] = licenseKey.flatMap(LicenseVerifier.validateLicense)

    val licenseHeader: String = deferLicenseHeader(licenseData, thirdPartyLicenseHeader)

    val tupleList =
      generateClassReps(ramlApiPath, apiPackageName, apiClassName, language)
        .collect { case clRep if clRep.content.isDefined => clRep }
        .map(addLicenseAndFormat(_, language, licenseHeader))
        .map(classRepToFilePathAndContent(_, language))

    mapAsJavaMap[String, String](tupleList.toMap)
  }


  private[generator] def generateClassReps(ramlApiPath: String,
                                           apiPackageName: String,
                                           apiClassName: String,
                                           language: Language): Seq[ClassRep] = {
    // Validate RAML spec
    println(s"Running RAML validation on $ramlApiPath: ")
    val validationResults: List[ValidationResult] = RamlParser.validateRaml(ramlApiPath)

    if (validationResults.nonEmpty) {
      sys.error(
        s"""
           |Invalid RAML specification:
           |
           |${RamlParser.printValidations(validationResults)}
           |
            |""".stripMargin
      )
    }
    println("RAML model is valid")

    // Generate the RAML model
    println("Running RAML model generation")
    val raml: Raml = RamlParser.buildRaml(ramlApiPath).asScala
    println(s"RAML model generated")

    // We need an implicit reference to the language we're generating the DSL for.
    implicit val lang = language

    val schemas: Map[String, Schema] = JsonSchemaParser.parse(raml.schemas)
    val schemaLookup: SchemaLookup = SchemaLookupParser.parse(schemas)
    println(s"Schema Lookup generated")

    val packageBasePath = apiPackageName.split('.').toList.filter(!_.isEmpty)

    val classMap: ClassMap = schemaLookup.classReps.values.map(classRep => classRep.classRef -> classRep).toMap
    val richResources = raml.resources.map(RichResource(_, packageBasePath, schemaLookup))


    language match {
      case Scala =>
        val caseClasses: Seq[ClassRep] = CaseClassGenerator.generateCaseClasses(classMap)
        println(s"Case classes generated")
        val resources: Seq[ClassRep] = ScalaResourceClassGenerator.generateResourceClasses(apiClassName, packageBasePath, richResources)
        println(s"Resources DSL generated")
        caseClasses ++ resources
      case Java  =>
        val pojos: Seq[ClassRep] = PojoGenerator.generatePojos(classMap)
        println(s"POJOs generated")
        val resources: Seq[ClassRep] = JavaResourceClassGenerator.generateResourceClasses(apiClassName, packageBasePath, richResources)
        println(s"Resources DSL generated")
        pojos ++ resources
    }

  }


  private val formatSettings =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(IndentSpaces, 2)


  private def addLicenseAndFormat(classRep: ClassRep, language: Language, licenseHeader: String): ClassRep = {
    val content = s"$licenseHeader\n${classRep.content.get}"
    val formattedContent = language match {
      case Scala => Try(ScalaFormatter.format(content, formatSettings)).getOrElse(content)
      case Java  => JavaFormatter.format(content) // ToDo: implement the Java code formatter.
    }
    classRep.withContent(formattedContent)
  }


  private val agplClassHeader =
    s"""|All rights reserved. This program and the accompanying materials
        |are made available under the terms of the GNU Affero General Public License
        |(AGPL) version 3.0 which accompanies this distribution, and is available in
        |the LICENSE file or at http://www.gnu.org/licenses/agpl-3.0.en.html
        |
        |This library is distributed in the hope that it will be useful,
        |but WITHOUT ANY WARRANTY; without even the implied warranty of
        |MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
        |Affero General Public License for more details.
     """.stripMargin


  private def classRepToFilePathAndContent(classRep: ClassRep, language: Language): (String, String) = {

    val classReference = classRep.classRef
    val pathParts = classReference.safePackageParts
    // It is important to start the foldLeft aggregate with new File(pathParts.head). If you start with new File("") and
    // start iterating from pathParts instead of pathParts.tail, then you'll get the wrong file path on Windows machines.
    //    val dir = pathParts.tail.foldLeft(new File(pathParts.head))((file, pathPart) => new File(file, pathPart))
    //    val file = new File(dir, s"${classRep.name}.scala")

    val extension = language match {
      case Scala => "scala"
      case Java  => "java"
    }

    val filePath = s"${pathParts.mkString(File.separator)}${File.separator}${classReference.name}.$extension"

    (filePath, classRep.content.getOrElse(s"No content generated for class ${classReference.fullyQualifiedName}"))
  }


  private def deferLicenseHeader(licenseKey: Option[LicenseData], thirdPartyLicenseHeader: Option[String]): String = {
    val classHeader =
      licenseKey.flatMap { licenseData =>
        thirdPartyLicenseHeader
      } getOrElse agplClassHeader
    classHeader.split('\n').map(line => s" $line").mkString("/**\n * ", "\n *", "/")
  }

}
