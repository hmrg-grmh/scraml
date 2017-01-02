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

package io.atomicbits.scraml.ramlparser.lookup.transformers

import io.atomicbits.scraml.ramlparser.lookup.{ CanonicalLookupHelper, CanonicalNameGenerator }
import io.atomicbits.scraml.ramlparser.model.canonicaltypes.{ CanonicalName, TypeParameter }
import io.atomicbits.scraml.ramlparser.model.parsedtypes.{ ParsedGenericObject, ParsedType }

/**
  * Created by peter on 30/12/16.
  */
object ParsedGenericObjectTransformer {

  def unapply(parsedTypeContext: ParsedTypeContext)(
      implicit canonicalNameGenerator: CanonicalNameGenerator): Option[(TypeParameter, CanonicalLookupHelper)] = { // format: on

    val parsed: ParsedType                           = parsedTypeContext.parsedType
    val canonicalLookupHelper: CanonicalLookupHelper = parsedTypeContext.canonicalLookupHelper
    val canonicalNameOpt: Option[CanonicalName]      = parsedTypeContext.canonicalNameOpt
    val parentNameOpt: Option[CanonicalName]         = parsedTypeContext.parentNameOpt // This is the optional json-schema parent

    def registerParsedGenericObject(parsedGenericObject: ParsedGenericObject): (TypeParameter, CanonicalLookupHelper) = {
      val typeParameter = TypeParameter(name = parsedGenericObject.typeVariable)
      (typeParameter, canonicalLookupHelper)
    }

    parsed match {
      case parsedGenericObject: ParsedGenericObject => Some(registerParsedGenericObject(parsedGenericObject))
      case _                                        => None
    }

  }

}
