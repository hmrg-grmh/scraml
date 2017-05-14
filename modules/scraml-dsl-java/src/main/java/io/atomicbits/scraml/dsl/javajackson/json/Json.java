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

package io.atomicbits.scraml.dsl.javajackson.json;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.atomicbits.scraml.dsl.javajackson.HttpParam;
import io.atomicbits.scraml.dsl.javajackson.SimpleHttpParam;

import java.io.IOException;
import java.util.*;

/**
 * Created by peter on 28/03/17.
 */
public class Json {

    /**
     * Reuse of ObjectMapper and JsonFactory is very easy: they are thread-safe provided that configuration is done before any use
     * (and from a single thread). After initial configuration use is fully thread-safe and does not need to be explicitly synchronized.
     * Source: http://wiki.fasterxml.com/JacksonBestPracticesPerformance
     */
    private static ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Write the body to a JSON string.
     * <p>
     * The main reason why we need the canonical form of the request type to serialize the body is in cases where
     * Java type erasure hides access to the Json annotations of our transfer objects.
     * <p>
     * Examples of such type erasure are cases where types in a hierarchy are put inside a {@code java.util.List<B>}. Then, the type of
     * {@code <B>} is hidden in {@code java.util.List<?>}, which hides the @JsonTypeInfo annotations for the objectmapper so that all type info
     * disappears form the resulting JSON objects.
     *
     * @param body                 The actual body.
     * @param canonicalRequestType The canonical form of the request body.
     * @param <B>                  The type of the body.
     * @return The JSON representation of the body as a string.
     */
    public static <B> String writeBodyToString(B body, String canonicalRequestType) {
        if (canonicalRequestType != null && !body.getClass().isEnum() && !body.getClass().isPrimitive()) {
            JavaType javaType = TypeFactory.defaultInstance().constructFromCanonical(canonicalRequestType);
            ObjectWriter writer = objectMapper.writerFor(javaType);
            try {
                return writer.writeValueAsString(body);
            } catch (IOException e) {
                throw new RuntimeException("JSON serialization error: " + e.getMessage(), e);
            }
        } else {
            return body.toString();
        }
    }

    public static <B> Map<String, HttpParam> toFormUrlEncoded(B body) {
        try {
            JsonNode jsonNode = objectMapper.valueToTree(body);
            Map<String, HttpParam> entries = new HashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = jsonNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (entry.getValue() != null) entries.put(entry.getKey(), new SimpleHttpParam(entry.getValue().asText())); // .asText() is essential here to avoid quoted strings
            }
            return entries;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
        }
    }

    public static <R> R parseBodyToObject(String body, String canonicalResponseType) {
        JavaType javaType = TypeFactory.defaultInstance().constructFromCanonical(canonicalResponseType);
        try {
            return objectMapper.readValue(body, javaType);
        } catch (IOException e) {
            throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
        }
    }

}