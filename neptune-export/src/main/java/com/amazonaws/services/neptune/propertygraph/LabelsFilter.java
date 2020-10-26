/*
Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License").
You may not use this file except in compliance with the License.
A copy of the License is located at
    http://www.apache.org/licenses/LICENSE-2.0
or in the "license" file accompanying this file. This file is distributed
on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
express or implied. See the License for the specific language governing
permissions and limitations under the License.
*/

package com.amazonaws.services.neptune.propertygraph;

import com.amazonaws.services.neptune.propertygraph.schema.GraphElementSchemas;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public interface LabelsFilter {

    LabelsFilter NULL_FILTER = new LabelsFilter() {
        @Override
        public GraphTraversal<? extends Element, ?> apply(GraphTraversal<? extends Element, ?> traversal) {
            return traversal;
        }

        @Override
        public Collection<Label> getLabelsUsing(GraphClient<?> graphClient) {
            return Collections.emptyList();
        }

        @Override
        public String[] getPropertiesForLabels(GraphElementSchemas graphElementSchemas) {
            return new String[]{};
        }

        @Override
        public Label getLabelFor(Map<String, Object> input) {
            return new Label("UNKNOWN");
        }

        @Override
        public String[] addAdditionalColumnNames(String... columns) {
            return new String[]{};
        }

        @Override
        public <T> GraphTraversal<? extends Element, T> addAdditionalColumns(GraphTraversal<? extends Element, T> t) {
            return t;
        }

        @Override
        public LabelsFilter filterFor(Label label) {
            return this;
        }
    };

    GraphTraversal<? extends Element, ?> apply(GraphTraversal<? extends Element, ?> traversal);

    Collection<Label> getLabelsUsing(GraphClient<?> graphClient);

    String[] getPropertiesForLabels(GraphElementSchemas graphElementSchemas);

    Label getLabelFor(Map<String, Object> input);

    String[] addAdditionalColumnNames(String... columns);

    <T> GraphTraversal<? extends Element, T> addAdditionalColumns(GraphTraversal<? extends Element, T> t);

    LabelsFilter filterFor(Label label);
}
