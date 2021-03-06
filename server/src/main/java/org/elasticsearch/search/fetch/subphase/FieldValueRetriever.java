/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.fetch.subphase;

import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.index.mapper.FieldAliasMapper;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.MappingLookup;
import org.elasticsearch.index.mapper.ValueFetcher;
import org.elasticsearch.search.lookup.SourceLookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class to {@link FetchFieldsPhase} that's initialized with a list of field patterns to fetch.
 * Then given a specific document, it can retrieve the corresponding fields from the document's source.
 */
public class FieldValueRetriever {
    private final List<FieldContext> fieldContexts;

    public static FieldValueRetriever create(MapperService mapperService,
                                             Collection<FieldAndFormat> fieldAndFormats) {
        MappingLookup fieldMappers = mapperService.documentMapper().mappers();
        List<FieldContext> fieldContexts = new ArrayList<>();

        for (FieldAndFormat fieldAndFormat : fieldAndFormats) {
            String fieldPattern = fieldAndFormat.field;
            String format = fieldAndFormat.format;

            Collection<String> concreteFields = mapperService.simpleMatchToFullName(fieldPattern);
            for (String field : concreteFields) {
                Mapper mapper = fieldMappers.getMapper(field);
                if (mapper == null || mapperService.isMetadataField(field)) {
                    continue;
                }

                if (mapper instanceof FieldAliasMapper) {
                    String target = ((FieldAliasMapper) mapper).path();
                    mapper = fieldMappers.getMapper(target);
                    assert mapper instanceof FieldMapper;
                }

                FieldMapper fieldMapper = (FieldMapper) mapper;
                ValueFetcher valueFetcher = fieldMapper.valueFetcher(mapperService, format);
                fieldContexts.add(new FieldContext(field, valueFetcher));
            }
        }

        return new FieldValueRetriever(fieldContexts);
    }

    private FieldValueRetriever(List<FieldContext> fieldContexts) {
        this.fieldContexts = fieldContexts;
    }

    public Map<String, DocumentField> retrieve(SourceLookup sourceLookup, Set<String> ignoredFields) {
        Map<String, DocumentField> documentFields = new HashMap<>();
        for (FieldContext context : fieldContexts) {
            String field = context.fieldName;
            if (ignoredFields.contains(field)) {
                continue;
            }

            ValueFetcher valueFetcher = context.valueFetcher;
            List<Object> parsedValues = valueFetcher.fetchValues(sourceLookup);

            if (parsedValues.isEmpty() == false) {
                documentFields.put(field, new DocumentField(field, parsedValues));
            }
        }
        return documentFields;
    }

    private static class FieldContext {
        final String fieldName;
        final ValueFetcher valueFetcher;

        FieldContext(String fieldName,
                     ValueFetcher valueFetcher) {
            this.fieldName = fieldName;
            this.valueFetcher = valueFetcher;
        }
    }
}
