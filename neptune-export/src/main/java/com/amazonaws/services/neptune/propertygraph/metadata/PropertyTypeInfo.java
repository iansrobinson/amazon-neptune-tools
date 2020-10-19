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

package com.amazonaws.services.neptune.propertygraph.metadata;

import java.util.List;
import java.util.Objects;

public class PropertyTypeInfo {

    private final Object property;
    private DataType dataType = DataType.None;
    private boolean isMultiValue = false;

    public PropertyTypeInfo(Object property) {
        this.property = property;
    }

    public PropertyTypeInfo(String property, DataType dataType, boolean isMultiValue) {
        this.property = property;
        this.dataType = dataType;
        this.isMultiValue = isMultiValue;
    }

    public Object property() {
        return property;
    }

    public void accept(Object value) {

        if (isList(value)) {
            List<?> values = (List<?>) value;
            if (values.size() > 1) {
                isMultiValue = true;
            }
            for (Object v : values) {
                dataType = DataType.getBroadestType(dataType, DataType.dataTypeFor(v.getClass()));
            }
        } else {
            dataType = DataType.getBroadestType(dataType, DataType.dataTypeFor(value.getClass()));
        }
    }

    private boolean isList(Object value) {
        return value.getClass().isAssignableFrom(java.util.ArrayList.class);
    }

    public DataType dataType() {
        return dataType;
    }

    public boolean isMultiValue() {
        return isMultiValue;
    }

    public String nameWithDataType() {
        return isMultiValue ?
                String.format("%s%s[]", propertyName(property), dataType.typeDescription()) :
                String.format("%s%s", propertyName(property), dataType.typeDescription());
    }

    public String nameWithoutDataType() {
        return propertyName(property);
    }

    private String propertyName(Object key) {
        if (key.equals(org.apache.tinkerpop.gremlin.structure.T.label)) {
            return "~label";
        }
        if (key.equals(org.apache.tinkerpop.gremlin.structure.T.id)) {
            return "~id";
        }
        if (key.equals(org.apache.tinkerpop.gremlin.structure.T.key)) {
            return "~key";
        }
        if (key.equals(org.apache.tinkerpop.gremlin.structure.T.value)) {
            return "~value";
        }
        return String.valueOf(key);
    }

    @Override
    public String toString() {
        return "PropertyTypeInfo{" +
                "property=" + property +
                ", dataType=" + dataType +
                ", isMultiValue=" + isMultiValue +
                '}';
    }

    public PropertyTypeInfo createCopy() {
        return new PropertyTypeInfo(property.toString(), dataType, isMultiValue);
    }

    public PropertyTypeInfo createRevision(PropertyTypeInfo propertyTypeInfo) {

        if (propertyTypeInfo.isMultiValue() == isMultiValue && propertyTypeInfo.dataType() == dataType) {
            return this;
        }

        boolean newIsMultiValue = propertyTypeInfo.isMultiValue() || isMultiValue;
        DataType newDataType = DataType.getBroadestType(dataType, propertyTypeInfo.dataType());

        return new PropertyTypeInfo(property.toString(), newDataType, newIsMultiValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyTypeInfo that = (PropertyTypeInfo) o;
        return isMultiValue == that.isMultiValue &&
                property.equals(that.property) &&
                dataType == that.dataType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, dataType, isMultiValue);
    }
}
