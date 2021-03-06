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

package io.atomicbits.scraml.ramlparser.lookup.transformers

import io.atomicbits.scraml.ramlparser.lookup.{ CanonicalLookupHelper, CanonicalNameGenerator }
import io.atomicbits.scraml.ramlparser.model.canonicaltypes._
import io.atomicbits.scraml.ramlparser.model.parsedtypes._

/**
  * Created by peter on 7/10/17.
  */
object ParsedDateTransformer {

  // format: off
  def unapply(parsedTypeContext: ParsedTypeContext)
             (implicit canonicalNameGenerator: CanonicalNameGenerator): Option[(TypeReference, CanonicalLookupHelper)] = { // format: on

    val parsed: ParsedType                           = parsedTypeContext.parsedType
    val canonicalLookupHelper: CanonicalLookupHelper = parsedTypeContext.canonicalLookupHelper
    val canonicalNameOpt: Option[CanonicalName]      = parsedTypeContext.canonicalNameOpt
    val parentNameOpt: Option[CanonicalName]         = parsedTypeContext.parentNameOpt // This is the optional json-schema parent

    parsed match {
      case parsedBoolean: ParsedDateOnly        => Some((DateOnlyType, canonicalLookupHelper))
      case parsedBoolean: ParsedTimeOnly        => Some((TimeOnlyType, canonicalLookupHelper))
      case parsedBoolean: ParsedDateTimeOnly    => Some((DateTimeOnlyType, canonicalLookupHelper))
      case parsedBoolean: ParsedDateTimeDefault => Some((DateTimeDefaultType, canonicalLookupHelper))
      case parsedBoolean: ParsedDateTimeRFC2616 => Some((DateTimeRFC2616Type, canonicalLookupHelper))
      case _                                    => None
    }
  }

}
