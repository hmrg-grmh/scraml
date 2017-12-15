/*
 *
 *  (C) Copyright 2015 Atomic BITS (http://atomicbits.io).
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the GNU Affero General Public License
 *  (AGPL) version 3.0 which accompanies this distribution, and is available in
 *  the LICENSE file or at http://www.gnu.org/licenses/agpl-3.0.en.html
 *  Alternatively, you may also use this code under the terms of the
 *  Scraml Commercial License, see http://scraml.io
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Affero General Public License or the Scraml Commercial License for more
 *  details.
 *
 *  Contributors:
 *      Peter Rigole
 *
 */

package io.atomicbits.scraml.dsl.androidjavajackson;

import io.atomicbits.scraml.dsl.androidjavajackson.json.Json;

/**
 * Created by peter on 7/04/17.
 */
public class ComplexHttpParam implements SingleHttpParam {

    private String parameter;

    public ComplexHttpParam(Object parameter, String canonicalType) {
        if (parameter != null) {
            String parameterFromJson = Json.writeBodyToString(parameter, canonicalType);
            if(parameterFromJson.startsWith("\"") && parameterFromJson.endsWith("\"")) {
                // When writing out a simple JSON-string value, we need to remove the quotes that Jackson has put around it.
                this.parameter = parameterFromJson.substring(1, parameterFromJson.length()-1);
            } else {
                // More complex JSON objects can be kept as they are.
                this.parameter = parameterFromJson;
            }
        }
    }

    @Override
    public String getParameter() {
        return parameter;
    }

    @Override
    public boolean nonEmpty() {
        return parameter != null;
    }

}
