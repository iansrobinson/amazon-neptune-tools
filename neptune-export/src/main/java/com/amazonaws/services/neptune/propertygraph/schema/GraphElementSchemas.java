/*
Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License").
You may not use this file except in compliance with the License.
A copy of the License is located at
    http://www.apache.org/licenses/LICENSE-2.0
or in the "license" file accompanying this file. This file is distributed
on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
express or implied. See the License for the specific language governing
permissions and limitations under the License.
*/

package com.amazonaws.services.neptune.propertygraph.schema;

import com.amazonaws.services.neptune.propertygraph.Label;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tinkerpop.gremlin.structure.T;

import java.util.*;

public class GraphElementSchemas {

    public static GraphElementSchemas fromJson(ArrayNode arrayNode) {

        GraphElementSchemas graphElementSchemas = new GraphElementSchemas();

        for (JsonNode node : arrayNode) {
            Label label = Label.fromJson(node.path("label"));

            graphElementSchemas.addLabelSchema(new LabelSchema(label));

            ArrayNode propertiesArray = (ArrayNode) node.path("properties");

            for (JsonNode propertyNode : propertiesArray) {

                String key = propertyNode.path("property").textValue();

                DataType dataType = Enum.valueOf(DataType.class, propertyNode.path("dataType").textValue());
                boolean isMultiValue = propertyNode.path("isMultiValue").booleanValue();
                boolean isNullable = propertyNode.has("isNullable") ?
                        propertyNode.path("isNullable").booleanValue() :
                        false;
                int minMultiValueSize = propertyNode.has("minMultiValueSize") ?
                        propertyNode.path("minMultiValueSize").intValue() :
                        0;
                int maxMultiValueSize = propertyNode.has("maxMultiValueSize") ?
                        propertyNode.path("maxMultiValueSize").intValue() :
                        0;

                graphElementSchemas.getSchemaFor(label).put(
                        key,
                        new PropertySchema(
                                key,
                                isNullable,
                                dataType,
                                isMultiValue,
                                minMultiValueSize,
                                maxMultiValueSize));
            }
        }

        return graphElementSchemas;
    }

    private final Map<Label, LabelSchemaContainer> labelSchemas = new HashMap<>();

    public void addLabelSchema(LabelSchema labelSchema) {
        addLabelSchema(labelSchema, Collections.emptyList());
    }

    public void addLabelSchema(LabelSchema labelSchema, Collection<String> outputIds) {
        labelSchemas.put(labelSchema.label(), new LabelSchemaContainer(labelSchema, outputIds));
    }

    public LabelSchema getSchemaFor(Label label) {

        if (!labelSchemas.containsKey(label)) {
            addLabelSchema(new LabelSchema(label));
        }

        return labelSchemas.get(label).labelSchema();
    }

    public Collection<String> getOutputIdsFor(Label label) {

        if (!labelSchemas.containsKey(label)) {
            return Collections.emptyList();
        }

        return labelSchemas.get(label).outputIds();
    }

    public boolean hasSchemaFor(Label label) {
        return labelSchemas.containsKey(label);
    }

    public void update(Map<?, ?> properties, boolean allowStructuralElements) {

        Object value = properties.get(T.label);

        Label label = List.class.isAssignableFrom(value.getClass()) ?
                new Label((List<String>) value) :
                new Label(String.valueOf(value));

        update(label, properties, allowStructuralElements);
    }

    public void update(Label label, Map<?, ?> properties, boolean allowStructuralElements) {

        LabelSchema labelSchema = getSchemaFor(label);

        for (PropertySchema propertySchema : labelSchema.propertySchemas()) {
            if (!properties.containsKey(propertySchema.property())) {
                propertySchema.makeNullable();
            }
        }

        for (Map.Entry<?, ?> entry : properties.entrySet()) {

            Object property = entry.getKey();

            if (allowStructuralElements || !(isToken(property))) {
                if (!labelSchema.containsProperty(property)) {
                    labelSchema.put(property, new PropertySchema(property));
                }
                labelSchema.getPropertySchema(property).accept(entry.getValue());
            }
        }
    }

    public Iterable<Label> labels() {
        return labelSchemas.keySet();
    }

    private boolean isToken(Object key) {
        return key.equals(T.label) || key.equals(T.id) || key.equals(T.key) || key.equals(T.value);
    }

    public ArrayNode toJson() {

        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();

        for (Map.Entry<Label, LabelSchemaContainer> entry : labelSchemas.entrySet()) {

            Label label = entry.getKey();

            ObjectNode labelNode = JsonNodeFactory.instance.objectNode();
            labelNode.set("label", label.toJson());

            ArrayNode propertiesNode = JsonNodeFactory.instance.arrayNode();

            LabelSchema labelSchema = entry.getValue().labelSchema();

            for (PropertySchema propertySchema : labelSchema.propertySchemas()) {

                ObjectNode propertyNode = JsonNodeFactory.instance.objectNode();
                propertyNode.put("property", propertySchema.property().toString());
                propertyNode.put("dataType", propertySchema.dataType().name());
                propertyNode.put("isMultiValue", propertySchema.isMultiValue());
                propertyNode.put("isNullable", propertySchema.isNullable());
                propertyNode.put("minMultiValueSize", propertySchema.minMultiValueSize());
                propertyNode.put("maxMultiValueSize", propertySchema.maxMultiValueSize());
                propertiesNode.add(propertyNode);
            }

            labelNode.set("properties", propertiesNode);

            arrayNode.add(labelNode);
        }

        return arrayNode;
    }

    public GraphElementSchemas createCopy() {
        return fromJson(toJson());
    }

    private static class LabelSchemaContainer {
        private final LabelSchema labelSchema;
        private final Collection<String> outputIds;

        private LabelSchemaContainer(LabelSchema labelSchema, Collection<String> outputIds) {
            this.labelSchema = labelSchema;
            this.outputIds = outputIds;
        }

        public LabelSchema labelSchema() {
            return labelSchema;
        }

        public Collection<String> outputIds() {
            return outputIds;
        }
    }
}
