/*
 *
 * (C) Copyright 2018 Atomic BITS (http://atomicbits.io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *  Contributors:
 *      Peter Rigole
 *
 */

package io.atomicbits.scraml.generator.platform.javajackson

import io.atomicbits.scraml.generator.platform.Platform

/**
  * Created by peter on 10/01/17.
  */
case class JavaJackson(apiBasePackageParts: List[String]) extends CommonJavaJacksonPlatform {

  implicit val platform: Platform = this

  val name: String = "Java Jackson"

  override val dslBasePackageParts: List[String] = List("io", "atomicbits", "scraml", "dsl", "javajackson")

  override val rewrittenDslBasePackage: List[String] = apiBasePackageParts ++ List("dsl", "javajackson")

}
