/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.plan.nodes.exec.serde;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.TableConfig;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.FunctionCatalog;
import org.apache.flink.table.module.ModuleManager;
import org.apache.flink.table.planner.calcite.FlinkTypeFactory;
import org.apache.flink.table.planner.catalog.CatalogManagerCalciteSchema;
import org.apache.flink.table.planner.delegation.ParserImpl;
import org.apache.flink.table.planner.delegation.PlannerContext;
import org.apache.flink.table.utils.CatalogManagerMocks;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonPointer;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectReader;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Collections;

import static org.apache.calcite.jdbc.CalciteSchemaBuilder.asRootSchema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

class JsonSerdeTestUtil {

    private JsonSerdeTestUtil() {
        // no instantiation
    }

    static SerdeContext configuredSerdeContext() {
        return configuredSerdeContext(
                CatalogManagerMocks.createEmptyCatalogManager(), TableConfig.getDefault());
    }

    static SerdeContext configuredSerdeContext(Configuration configuration) {
        final TableConfig tableConfig = TableConfig.getDefault();
        tableConfig.addConfiguration(configuration);
        return configuredSerdeContext(
                CatalogManagerMocks.createEmptyCatalogManager(), configuration);
    }

    static SerdeContext configuredSerdeContext(
            CatalogManager catalogManager, Configuration configuration) {
        final TableConfig tableConfig = TableConfig.getDefault();
        tableConfig.addConfiguration(configuration);
        return configuredSerdeContext(catalogManager, tableConfig);
    }

    static SerdeContext configuredSerdeContext(
            CatalogManager catalogManager, TableConfig tableConfig) {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final ModuleManager moduleManager = new ModuleManager();
        final FunctionCatalog functionCatalog =
                new FunctionCatalog(tableConfig, catalogManager, moduleManager);
        final PlannerContext plannerContext =
                new PlannerContext(
                        false,
                        tableConfig,
                        moduleManager,
                        functionCatalog,
                        catalogManager,
                        asRootSchema(new CatalogManagerCalciteSchema(catalogManager, true)),
                        Collections.emptyList());
        return new SerdeContext(
                new ParserImpl(null, null, plannerContext::createCalciteParser, null),
                plannerContext.getFlinkContext(),
                classLoader,
                FlinkTypeFactory.INSTANCE(),
                plannerContext.createFrameworkConfig().getOperatorTable());
    }

    static String toJson(SerdeContext serdeContext, Object object) throws IOException {
        final ObjectWriter objectWriter = JsonSerdeUtil.createObjectWriter(serdeContext);
        return objectWriter.writeValueAsString(object);
    }

    static <T> T toObject(SerdeContext serdeContext, String json, Class<T> clazz)
            throws IOException {
        final ObjectReader objectReader = JsonSerdeUtil.createObjectReader(serdeContext);
        return objectReader.readValue(json, clazz);
    }

    static <T> T testJsonRoundTrip(SerdeContext serdeContext, T spec, Class<T> clazz)
            throws IOException {
        String actualJson = toJson(serdeContext, spec);
        T actual = toObject(serdeContext, actualJson, clazz);

        assertThat(actual).isEqualTo(spec);
        return actual;
    }

    static <T> T testJsonRoundTrip(T spec, Class<T> clazz) throws IOException {
        return testJsonRoundTrip(configuredSerdeContext(), spec, clazz);
    }

    static void assertThatJsonContains(JsonNode json, String... path) {
        JsonPointer jsonPointer = pathToPointer(path);
        assertThat(json)
                .asInstanceOf(type(ObjectNode.class))
                .as(
                        "Serialized json '%s' contains at pointer '%s' a not null value",
                        jsonPointer, json)
                .matches(
                        o -> {
                            JsonNode node = o.at(jsonPointer);
                            return !node.isMissingNode() && !node.isNull();
                        });
    }

    static void assertThatJsonDoesNotContain(JsonNode json, String... path) {
        JsonPointer jsonPointer = pathToPointer(path);
        assertThat(json)
                .asInstanceOf(type(ObjectNode.class))
                .as(
                        "Serialized json '%s' at pointer '%s' return missing node or null node",
                        jsonPointer, json)
                .matches(
                        o -> {
                            JsonNode node = o.at(jsonPointer);
                            return node.isMissingNode() || node.isNull();
                        });
    }

    private static JsonPointer pathToPointer(String... path) {
        JsonPointer pointer = JsonPointer.empty();
        for (String el : path) {
            pointer = pointer.append(JsonPointer.compile(JsonPointer.SEPARATOR + el));
        }
        return pointer;
    }
}
