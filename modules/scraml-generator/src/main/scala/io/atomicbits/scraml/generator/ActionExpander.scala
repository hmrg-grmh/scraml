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

import io.atomicbits.scraml.jsonschemaparser.SchemaLookup
import io.atomicbits.scraml.parser.model._

import scala.reflect.macros.whitebox

/**
 * Created by peter on 24/05/15, Atomic BITS (http://atomicbits.io). 
 */
object ActionExpander {

  def expandAction(action: Action, schemaLookup: SchemaLookup, c: whitebox.Context): List[c.universe.Tree] = {

    import c.universe._


    // We currently only support the first context-type mimeType that we see. We should extend this later on.
    val bodyMimeType = action.body.values.toList.headOption
    val maybeBodyRootId = bodyMimeType.flatMap(_.schema).flatMap(schemaLookup.externalSchemaLinks.get)
    val maybeBodyClassName = maybeBodyRootId.flatMap(schemaLookup.canonicalNames.get)

    val formParameters: Map[String, List[Parameter]] = bodyMimeType.map(_.formParameters).getOrElse(Map.empty)

    // We currently only support the first response mimeType that we see. We should extend this later on.
    val response = action.responses.values.toList.headOption
    // We currently only support the first response body mimeType that we see. We should extend this later on.
    val responseMimeType = response.flatMap(_.body.values.toList.headOption)
    val maybeResponseRootId = responseMimeType.flatMap(_.schema).flatMap(schemaLookup.externalSchemaLinks.get)
    val maybeResponseClassName = maybeResponseRootId.flatMap(schemaLookup.canonicalNames.get)

    val (hasJsonDtoBody, bodyClassName) = maybeBodyClassName match {
      case Some(bdClass) => (true, bdClass)
      case None => (false, "String")
    }

    val (hasJsonDtoResponse, responseClassName) = maybeResponseClassName match {
      case Some(rsClass) => (true, rsClass)
      case None => (false, "String")
    }



    def expandGetAction(): List[c.universe.Tree] = {

      val queryParameterMethodParameters =
        action.queryParameters.toList.map(param => expandParameterAsMethodParameter(param))
      val queryParameterMapEntries =
        action.queryParameters.toList.map(param => expandParameterAsMapEntry(param))

      List(
        q"""
          def get(..$queryParameterMethodParameters) = new GetSegment(
            queryParams = Map(
              ..$queryParameterMapEntries
            ),
            validAcceptHeaders = List(..${validAcceptHeaders()}),
            req = requestBuilder
          ) {

            ..${expandHeaders(hasBody = false, "String")}

          }
       """)
    }

    def expandPutAction(): List[c.universe.Tree] = {
      List(
        q"""
          def put(body: String) = new PutSegment(
            validAcceptHeaders = List(..${validAcceptHeaders()}),
            validContentTypeHeaders = List(..${validContentTypeHeaders()}),
            req = requestBuilder) {

            ..${expandHeaders(hasBody = true, "String")}

          }
       """,
        q"""
          def put(body: JsValue) = new PutSegment(
            validAcceptHeaders = List(..${validAcceptHeaders()}),
            validContentTypeHeaders = List(..${validContentTypeHeaders()}),
            req = requestBuilder) {

            ..${expandHeaders(hasBody = true, "JsValue")}

          }
       """
      )
    }

    def expandPostAction(): List[c.universe.Tree] = {

      if (formParameters.isEmpty) {
        // We support a custom body instead.
        val defaultActions =
          List(
            q"""
          def post(body: String) = new PostSegment(
            formParams = Map.empty,
            validAcceptHeaders = List(..${validAcceptHeaders()}),
            validContentTypeHeaders = List(..${validContentTypeHeaders()}),
            req = requestBuilder) {

            ..${expandHeaders(hasBody = true, "String")}

          }
       """,
            q"""
          def post(body: JsValue) = new PostSegment(
            formParams = Map.empty,
            validAcceptHeaders = List(..${validAcceptHeaders()}),
            validContentTypeHeaders = List(..${validContentTypeHeaders()}),
            req = requestBuilder) {

            ..${expandHeaders(hasBody = true, "JsValue")}

          }
       """
          )

        val additionalAction =
          if (hasJsonDtoBody) {
            val typeTypeName = TypeName(bodyClassName)
            val bodyParam = List(q"val body: $typeTypeName")
            List(
              q"""
                  def post(..$bodyParam) = new PostSegment(
                    formParams = Map.empty,
                    validAcceptHeaders = List(..${validAcceptHeaders()}),
                    validContentTypeHeaders = List(..${validContentTypeHeaders()}),
                    req = requestBuilder) {

                      ..${expandHeaders(hasBody = true, bodyClassName)}

                  }
                """
            )
          } else Nil

        defaultActions ++ additionalAction

      } else {
        // We support the given form parameters.
        val formParameterMethodParameters =
          formParameters.toList.map { paramPair =>
            val (name, paramList) = paramPair
            if (paramList.isEmpty) sys.error(s"Form parameter $name has no valid type definition.")
            expandParameterAsMethodParameter((name, paramList.head))
            // We still don't understand why the form parameters are represented as a Map[String, List[Parameter]]
            // instead of just a Map[String, Parameter] in the Java Raml model. Here, we just use the first element
            // of the parameter list.
          }

        val formParameterMapEntries =
          formParameters.toList.map { paramPair =>
            val (name, paramList) = paramPair
            expandParameterAsMapEntry((name, paramList.head))
          }

        List(
          q"""
          def post(..$formParameterMethodParameters) = new PostSegment(
            formParams = Map(
              ..$formParameterMapEntries
            ),
            validAcceptHeaders = List(..${validAcceptHeaders()}),
            validContentTypeHeaders = List(..${validContentTypeHeaders()}),
            req = requestBuilder) {

              ..${expandHeaders(hasBody = false, "String")}

          }
       """)
      }

    }

    def expandDeleteAction(): List[c.universe.Tree] = {
      List(
        q"""
          def delete() = new DeleteSegment(
            validAcceptHeaders = List(..${validAcceptHeaders()}),
            validContentTypeHeaders = List(..${validContentTypeHeaders()}),
            req = requestBuilder) {

            ..${expandHeaders(hasBody = false, "String")}

          }
       """)
    }

    def expandHeaders(hasBody: Boolean, bodyClassName: String): List[c.universe.Tree] = {
      List(
        q"""
           def headers(headers: (String, String)*) = new HeaderSegment(
             headers = headers.toMap,
             req = requestBuilder
           ) {
             ..${expandExecution(hasBody,bodyClassName)}
           }
         """
      ) ++ expandExecution(hasBody,bodyClassName)
    }

    def expandExecution(hasBody: Boolean, bodyClassName: String): List[c.universe.Tree] = {
      val bodyTypeName = TypeName(bodyClassName)
      val responseTypeName = TypeName(responseClassName)
      val executeSegment =
        if (hasBody) {
          q"""
           private val executeSegment = new ExecuteSegment[$bodyTypeName, $responseTypeName](requestBuilder, Some(body))
         """
        } else {
          q"""
           private val executeSegment = new ExecuteSegment[$bodyTypeName, $responseTypeName](requestBuilder, None)
         """
        }
      val jsonDtoExecutor =
        if (hasJsonDtoResponse) List( q""" def executeToJsonDto() = executeSegment.executeToJsonDto() """)
        else Nil
      List(
        executeSegment,
        q""" def execute() = executeSegment.execute() """,
        q""" def executeToJson() = executeSegment.executeToJson() """
      ) ++ jsonDtoExecutor
    }

    def needsAcceptHeader: Boolean = {
      action.responses.values.toList.flatMap(_.headers).nonEmpty
    }

    def validAcceptHeaders(): List[c.universe.Tree] = {
      action.responses.values.toList.flatMap(response => response.headers.keys.map(header => q"$header"))
    }

    def needsContentTypeHeader: Boolean = {
      action.body.keys.toList.nonEmpty
    }

    def validContentTypeHeaders(): List[c.universe.Tree] = {
      action.body.keys.toList.map(header => q"$header")
    }

    def expandParameterAsMethodParameter(qParam: (String, Parameter)): c.universe.Tree = {
      val (queryParameterName, parameter) = qParam

      val nameTermName = TermName(queryParameterName)
      val typeTypeName = parameter.parameterType match {
        case StringType => TypeName("String")
        case IntegerType => TypeName("Int")
        case NumberType => TypeName("Double")
        case BooleanType => TypeName("Boolean")
        case FileType => sys.error(s"RAML type 'FileType' is not yet supported.")
        case DateType => sys.error(s"RAML type 'DateType' is not yet supported.")
      }

      if (parameter.required) {
        q"val $nameTermName: $typeTypeName"
      } else {
        q"val $nameTermName: Option[$typeTypeName]"
      }
    }

    def expandParameterAsMapEntry(qParam: (String, Parameter)): c.universe.Tree = {
      val (queryParameterName, parameter) = qParam
      val nameTermName = TermName(queryParameterName)
      parameter match {
        case Parameter(_, true) => q"""$queryParameterName -> Option($nameTermName).map(_.toString)"""
        case Parameter(_, false) => q"""$queryParameterName -> $nameTermName.map(_.toString)"""
      }
    }


    action.actionType match {
      case Get => expandGetAction()
      case Put => expandPutAction()
      case Post => expandPostAction()
      case Delete => expandDeleteAction()
      case unknownAction => sys.error(s"$unknownAction actions are not supported yet.")
    }

  }

}