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

package com.amazonaws.services.neptune.export;

import com.amazonaws.services.neptune.propertygraph.ExportStats;
import com.amazonaws.services.neptune.propertygraph.schema.GraphSchema;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;

public class EventHandlerCollection implements NeptuneExportEventHandler {

    private final Collection<NeptuneExportEventHandler> handlers;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(EventHandlerCollection.class);

    public EventHandlerCollection(Collection<NeptuneExportEventHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void onExportComplete(Path outputPath, ExportStats stats) throws Exception {
        for (NeptuneExportEventHandler handler : handlers) {
            try {
                handler.onExportComplete(outputPath, stats);
            } catch (Exception e) {
                logger.error("Error while executing {}", handler.getClass().getSimpleName(), e);
            }
        }
    }

    @Override
    public void onExportComplete(Path outputPath, ExportStats stats, GraphSchema graphSchema) throws Exception {
        for (NeptuneExportEventHandler handler : handlers) {
            try {
                handler.onExportComplete(outputPath, stats, graphSchema);
            } catch (Exception e) {
                logger.error("Error while executing {}", handler.getClass().getSimpleName(), e);
            }
        }
    }
}