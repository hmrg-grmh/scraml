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

package io.atomicbits.scraml.ramlparser.model.parsedtypes

import io.atomicbits.scraml.ramlparser.lookup.OldCanonicalLookupHelper
import io.atomicbits.scraml.ramlparser.model._
import io.atomicbits.scraml.ramlparser.parser.{ParseContext, RamlParseException}
import io.atomicbits.scraml.util.TryUtils
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

/**
  * Created by peter on 25/03/16.
  */
case class ParsedObject(id: Id,
                        baseType: List[Id],
                        properties: ParsedProperties,
                        required: Option[Boolean] = None,
                        requiredProperties: List[String] = List.empty,
                        selection: Option[Selection] = None,
                        fragments: Fragments = Fragments(),
                        parent: Option[UniqueId] = None,
                        children: List[UniqueId] = List.empty,
                        typeParameters: List[String] = List.empty,
                        typeDiscriminator: Option[String] = None,
                        typeDiscriminatorValue: Option[String] = None,
                        model: TypeModel = RamlModel) extends Fragmented with AllowedAsObjectField with NonPrimitiveType {

  override def updated(updatedId: Id): ParsedObject = copy(id = updatedId)

  override def asTypeModel(typeModel: TypeModel): ParsedType = {
    val updatedProperties =
      properties.map { property =>
        property.copy(propertyType = TypeRepresentation(property.propertyType.parsed.asTypeModel(typeModel)))
      }
    copy(model = typeModel, properties = updatedProperties)
  }

  def hasChildren: Boolean = children.nonEmpty

  def hasParent: Boolean = parent.isDefined

  def isInTypeHiearchy: Boolean = hasChildren || hasParent

  def topLevelParent(typeLookup: OldCanonicalLookupHelper): Option[ParsedObject] = {

    def findTopLevelParent(uniqueId: UniqueId): ParsedObject = {
      val objElExt = typeLookup.objectMap(uniqueId)
      objElExt.parent match {
        case Some(parentId) => findTopLevelParent(parentId)
        case None           => objElExt
      }
    }

    parent.map(findTopLevelParent)

  }

}


object ParsedObject {

  val value = "object"


  def apply(json: JsValue)(implicit parseContext: ParseContext): Try[ParsedObject] = {

    val model: TypeModel = TypeModel(json)

    // Process the id
    val id: Id = json match {
      case IdExtractor(schemaId) => schemaId
    }

    // Process the properties
    val properties: Try[ParsedProperties] = ParsedProperties((json \ "properties").toOption, model)

    val fragments = json match {
      case Fragments(fragment) => fragment
    }

    // Process the required field
    val (required, requiredFields) =
    json \ "required" toOption match {
      case Some(req: JsArray) =>
        (None, Some(req.value.toList collect {
          case JsString(value) => value
        }))
      case Some(JsBoolean(b)) => (Some(b), None)
      case _                  => (None, None)
    }


    // Process the typeVariables field
    val typeVariables: List[String] =
    json \ "typeVariables" toOption match {
      case Some(typeVars: JsArray) => typeVars.value.toList.collect { case JsString(value) => value }
      case _                       => List.empty[String]
    }

    // Process the typeDiscriminator field
    val typeDiscriminator: Option[String] =
    json \ "typeDiscriminator" toOption match {
      case Some(JsString(value)) => Some(value)
      case _                     => None
    }

    val oneOf =
      (json \ "oneOf").toOption collect {
        case selections: JsArray =>
          val selectionSchemas = selections.value collect {
            case jsObj: JsObject => jsObj
          } map (tryToInterpretOneOfSelectionAsObjectType(_, id, typeDiscriminator.getOrElse("type")))
          TryUtils.accumulate(selectionSchemas.toList).map(selections => OneOf(selections.map(_.asTypeModel(JsonSchemaModel))))
      }

    val anyOf =
      (json \ "anyOf").toOption collect {
        case selections: JsArray =>
          val selectionSchemas = selections.value collect {
            case ParsedType(theType) => theType
          }
          TryUtils.accumulate(selectionSchemas.toList).map(selections => AnyOf(selections.map(_.asTypeModel(JsonSchemaModel))))
      }

    val allOf =
      (json \ "allOf").toOption collect {
        case selections: JsArray =>
          val selectionSchemas = selections.value collect {
            case ParsedType(theType) => theType
          }
          TryUtils.accumulate(selectionSchemas.toList).map(selections => AllOf(selections.map(_.asTypeModel(JsonSchemaModel))))
      }

    val selection = List(oneOf, anyOf, allOf).flatten.headOption


    TryUtils.withSuccess(
      Success(id),
      Success(List.empty[Id]),
      properties,
      Success(required),
      Success(requiredFields.getOrElse(List.empty[String])),
      TryUtils.accumulate(selection),
      fragments,
      Success(None),
      Success(List.empty[AbsoluteId]),
      Success(typeVariables),
      Success(typeDiscriminator),
      Success(None),
      Success(model)
    )(new ParsedObject(_, _, _, _, _, _, _, _, _, _, _, _, _))
  }


  def unapply(json: JsValue)(implicit parseContext: ParseContext): Option[Try[ParsedObject]] = {

    def isParentRef(theOtherType: String): Option[Try[ParsedTypeReference]] = {
      ParsedType(theOtherType) match {
        case typeRef: Try[ParsedTypeReference] => Some(typeRef) // It is not a primitive type and not an array, so it is a type reference.
        case _                                 => None
      }
    }


    (ParsedType.typeDeclaration(json), (json \ "properties").toOption, (json \ "genericType").toOption) match {
      case (Some(JsString(ParsedObject.value)), _, None)  => Some(ParsedObject(json))
      case (Some(JsString(otherType)), Some(jsObj), None) =>
        isParentRef(otherType).map { triedParent =>
          triedParent.flatMap { parent =>
            ParsedObject(json).flatMap { objectType =>
              parent.refersTo match {
                case nativeId: NativeId => Success(objectType.copy(parent = Some(nativeId)))
                case _                  => Failure(RamlParseException(s"Expected a parent reference in RAML1.0 to have a valid native id."))
              }
            }
          }
        }
      case (None, Some(jsObj), None)                      => Some(ParsedObject(json))
      case _                                              => None
    }

  }


  def schemaToDiscriminatorValue(schema: Identifiable): Option[String] = {
    Some(schema) collect {
      case enumType: ParsedEnum if enumType.choices.length == 1 => enumType.choices.head
    }
  }


  private def tryToInterpretOneOfSelectionAsObjectType(schema: JsObject, parentId: Id, typeDiscriminator: String)
                                                      (implicit parseContext: ParseContext): Try[ParsedType] = {

    def typeDiscriminatorFromProperties(oneOfFragment: Fragments): Option[String] = {
      oneOfFragment.fragmentMap.get("properties") collect {
        case propFrag: Fragments => propFrag.fragmentMap.get(typeDiscriminator) flatMap schemaToDiscriminatorValue
      } getOrElse None
    }

    def fixId(id: Id, parentId: Id, discriminatorValue: String): Option[RelativeId] = {
      id match {
        case ImplicitId => // fix id based on the parentId if there isn't one
          if (discriminatorValue.exists(_.isLower)) Some(RelativeId(id = discriminatorValue))
          else Some(RelativeId(id = discriminatorValue.toLowerCase))
        case _          => None
      }
    }

    // removed this from the following match case:
    // case ObjectType(objectType) => objectType
    //    case Fragment(fragment)     =>
    //    fragment.flatMap { frag =>
    //      typeDiscriminatorFromProperties(frag).flatMap(fixId(frag.id, parentId, _)) map { relativeId =>
    //        schema + ("type" -> JsString("object")) + ("id" -> JsString(relativeId.id)) match {
    //          case Type(x) => x
    //        }
    //      } getOrElse Success(frag)
    //    }

    schema match {
      case ParsedType(x) => x
    }

  }

}