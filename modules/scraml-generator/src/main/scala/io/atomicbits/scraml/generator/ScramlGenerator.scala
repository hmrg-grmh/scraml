/*
 * (C) Copyright 2015 Atomic BITS (http://atomicbits.io).
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Affero General Public License
 * (AGPL) version 3.0 which accompanies this distribution, and is available in
 * the LICENSE file or at http://www.gnu.org/licenses/agpl-3.0.en.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * Contributors:
 *     Peter Rigole
 *
 */

package io.atomicbits.scraml.generator

import io.atomicbits.scraml.jsonschemaparser.{SchemaLookup, JsonSchemaParser}
import org.raml.parser.rule.ValidationResult

import io.atomicbits.scraml.parser._
import io.atomicbits.scraml.parser.model._

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros._


// Selective packaging: http://www.scala-sbt.org/sbt-native-packager/formats/universal.html
// Macro projects: http://www.scala-sbt.org/0.13/docs/Macro-Projects.html (macro module in same project as core module)

// What we need is:
// http://stackoverflow.com/questions/21515325/add-a-compile-time-only-dependency-in-sbt

class ScRaml(ramlSpecPath: String) extends StaticAnnotation {

  def macroTransform(annottees: Any*): Any = macro ScRamlGenerator.generate

}

object ScRamlGenerator {

  // Macro annotations must be whitebox. If you declare a macro annotation as blackbox, it will not work.
  // See: http://docs.scala-lang.org/overviews/macros/annotations.html
  def generate(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {

    import c.universe._

    val ramlSpecPath = c.prefix.tree match {
      case Apply(_, List(Literal(Constant(x)))) => x.toString
      case _ => c.abort(c.enclosingPosition, "RAML specification path not specified")
    }

    val className = annottees.map(_.tree) match {
      case List(q"class $name") => name
      case _ => c.abort(c.enclosingPosition, "the annotation can only be used with classes")
    }

    val classAsTermName = TermName(className.toString)

    // Validate RAML spec
    println(s"Running RAML validation on $ramlSpecPath: ")
    val validationResults: List[ValidationResult] = RamlParser.validateRaml(ramlSpecPath)
    if (validationResults.nonEmpty) {
      println("Invalid RAML specification:")
      c.abort(c.enclosingPosition, RamlParser.printValidations(validationResults))
    }
    println("RAML model is valid")

    // Generate the RAML model
    println("Running RAML model generation")
    val raml: Raml = RamlParser.buildRaml(ramlSpecPath).asScala
    println(s"RAML model generated")

    val schemaLookup: SchemaLookup = JsonSchemaParser.parse(raml.schemas)
    println(s"Schema Lookup generated")

    val caseClasses = CaseClassGenerator.generateCaseClasses(schemaLookup, c)
    println(s"Case classes generated: $caseClasses")

    val resources = raml.resources.map(resource => ResourceExpander.expandResource(resource, schemaLookup, c))
    println(s"Resources DSL generated")

    // ToDo: process enumerations
    //    val enumObjects = CaseClassGenerator.generateEnumerationObjects(schemaLookup, c)

    // rewrite the class definition
    c.Expr(
      q"""
       case class $className(host: String,
                             port: Int = 80,
                             protocol: String = "http",
                             requestTimeout: Int = 5000,
                             maxConnections: Int = 2,
                             defaultHeaders: Map[String, String] = Map.empty) {

         import io.atomicbits.scraml.dsl.support._
         import io.atomicbits.scraml.dsl.support.client.rxhttpclient.RxHttpClient

         import $classAsTermName._

         protected val requestBuilder = RequestBuilder(new RxHttpClient(protocol, host, port, requestTimeout, maxConnections))

         ..$resources

       }


       object $classAsTermName {

         import play.api.libs.json.{Json, Format}

         ..$caseClasses

       }

     """


    )

  }


}

