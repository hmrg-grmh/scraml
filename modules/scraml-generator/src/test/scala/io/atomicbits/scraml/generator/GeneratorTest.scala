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

import io.atomicbits.scraml.generator.model._
import io.atomicbits.scraml.jsonschemaparser.RootId
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._


/**
 * Created by peter on 10/09/15. 
 */
class GeneratorTest extends FeatureSpec with GivenWhenThen with BeforeAndAfterAll with ScalaFutures {

  feature("The scraml generator generates DSL classes") {

    scenario("test generated Scala DSL") {

      Given("a RAML specification")
      val apiResourceUrl = this.getClass.getClassLoader.getResource("io/atomicbits/scraml/TestApi.raml")

      When("we generate the RAMl specification into class representations")
      val classReps: Seq[ClassRep] =
        ScramlGenerator.generateClassReps(
          ramlApiPath = apiResourceUrl.toString,
          apiPackageName = "io.atomicbits.scraml",
          apiClassName = "TestApi",
          Scala
        )

      Then("we should get valid class representations")
      val classRepsByFullName: Map[String, ClassRep] = classReps.map(rep => rep.fullyQualifiedName -> rep).toMap

      val classes = List(
        "io.atomicbits.scraml.TestApi",
        "io.atomicbits.scraml.rest.RestResource",
        "io.atomicbits.scraml.rest.user.UserResource",
        "io.atomicbits.scraml.rest.user.userid.dogs.DogsResource",
        "io.atomicbits.scraml.rest.user.userid.UseridResource",
        "io.atomicbits.scraml.rest.user.userid.AcceptApplicationVndV01JsonHeaderSegment",
        "io.atomicbits.scraml.rest.user.userid.AcceptApplicationVndV10JsonHeaderSegment",
        "io.atomicbits.scraml.rest.user.userid.ContentApplicationVndV01JsonHeaderSegment",
        "io.atomicbits.scraml.rest.user.userid.ContentApplicationVndV10JsonHeaderSegment",
        "io.atomicbits.scraml.rest.user.upload.UploadResource",
        "io.atomicbits.scraml.rest.user.activate.ActivateResource",
        "io.atomicbits.scraml.rest.animals.AnimalsResource",
        "io.atomicbits.schema.User",
        "io.atomicbits.schema.UserDefinitionsAddress",
        "io.atomicbits.schema.Link",
        "io.atomicbits.schema.PagedList",
        "io.atomicbits.schema.Animal",
        "io.atomicbits.schema.Dog",
        "io.atomicbits.schema.Cat",
        "io.atomicbits.schema.Fish",
        "io.atomicbits.schema.Method",
        "io.atomicbits.schema.Geometry",
        "io.atomicbits.schema.Point",
        "io.atomicbits.schema.LineString",
        "io.atomicbits.schema.MultiPoint",
        "io.atomicbits.schema.MultiLineString",
        "io.atomicbits.schema.Polygon",
        "io.atomicbits.schema.MultiPolygon",
        "io.atomicbits.schema.GeometryCollection",
        "io.atomicbits.schema.Crs",
        "io.atomicbits.schema.NamedCrsProperty",
        "io.atomicbits.schema.Bbox"
      )

      classRepsByFullName.keys.foreach { key =>
        assert(classes.contains(key), s"Class $key is not generated.")
      }

      val userResource = classRepsByFullName("io.atomicbits.scraml.rest.user.UserResource")
      val expectedUserResource = CustomClassRep(
        classRef = ClassReference("UserResource", List("io", "atomicbits", "scraml", "rest", "user")),
        List(),
        None,
        List(),
        Some(""),
        None
      )
      assert(userResource.withContent("") == expectedUserResource)

      val animalClass = classRepsByFullName("io.atomicbits.schema.Animal")
      val expectedAnimalClassRep = CustomClassRep(
        classRef = ClassReference("Animal", List("io", "atomicbits", "schema")),
        List(),
        None,
        List(
          ClassReference("Cat", List("io", "atomicbits", "schema")),
          ClassReference("Dog", List("io", "atomicbits", "schema")),
          ClassReference("Fish", List("io", "atomicbits", "schema"))
        ),
        Some(""),
        Some(JsonTypeInfo("_type", None))
      )
      assert(animalClass.withContent("") == expectedAnimalClassRep)
    }



    scenario("test generated Java DSL") {

      Given("a RAML specification")
      val apiResourceUrl = this.getClass.getClassLoader.getResource("io/atomicbits/scraml/TestApi.raml")

      When("we generate the RAMl specification into class representations")
      val classReps: Seq[ClassRep] =
        ScramlGenerator.generateClassReps(
          ramlApiPath = apiResourceUrl.toString,
          apiPackageName = "io.atomicbits.scraml",
          apiClassName = "TestApi",
          Java
        )

      Then("we should get valid class representations")
      val classRepsByFullName: Map[String, ClassRep] = classReps.map(rep => rep.fullyQualifiedName -> rep).toMap

      val classes = List(
        "io.atomicbits.scraml.TestApi",
        "io.atomicbits.scraml.rest.RestResource",
        "io.atomicbits.scraml.rest.user.UserResource",
        "io.atomicbits.scraml.rest.user.userid.dogs.DogsResource",
        "io.atomicbits.scraml.rest.user.userid.UseridResource",
        "io.atomicbits.scraml.rest.user.userid.AcceptApplicationVndV01JsonHeaderSegment",
        "io.atomicbits.scraml.rest.user.userid.AcceptApplicationVndV10JsonHeaderSegment",
        "io.atomicbits.scraml.rest.user.userid.ContentApplicationVndV01JsonHeaderSegment",
        "io.atomicbits.scraml.rest.user.userid.ContentApplicationVndV10JsonHeaderSegment",
        "io.atomicbits.scraml.rest.user.upload.UploadResource",
        "io.atomicbits.scraml.rest.user.activate.ActivateResource",
        "io.atomicbits.scraml.rest.animals.AnimalsResource",
        "io.atomicbits.schema.User",
        "io.atomicbits.schema.UserDefinitionsAddress",
        "io.atomicbits.schema.Link",
        "io.atomicbits.schema.PagedList",
        "io.atomicbits.schema.Animal",
        "io.atomicbits.schema.Dog",
        "io.atomicbits.schema.Cat",
        "io.atomicbits.schema.Fish",
        "io.atomicbits.schema.Method",
        "io.atomicbits.schema.Geometry",
        "io.atomicbits.schema.Point",
        "io.atomicbits.schema.LineString",
        "io.atomicbits.schema.MultiPoint",
        "io.atomicbits.schema.MultiLineString",
        "io.atomicbits.schema.Polygon",
        "io.atomicbits.schema.MultiPolygon",
        "io.atomicbits.schema.GeometryCollection",
        "io.atomicbits.schema.Crs",
        "io.atomicbits.schema.NamedCrsProperty",
        "io.atomicbits.schema.Bbox"
      )

      classRepsByFullName.keys.foreach { key =>
        assert(classes.contains(key), s"Class $key is not generated.")
      }


    }

  }
}
