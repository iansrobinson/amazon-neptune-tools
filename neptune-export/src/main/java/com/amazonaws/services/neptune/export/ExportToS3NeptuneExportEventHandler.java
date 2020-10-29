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

package com.amazonaws.services.neptune.export;

import com.amazonaws.services.neptune.propertygraph.ExportStats;
import com.amazonaws.services.neptune.propertygraph.schema.GraphSchema;
import com.amazonaws.services.neptune.util.CheckedActivity;
import com.amazonaws.services.neptune.util.S3ObjectInfo;
import com.amazonaws.services.neptune.util.Timer;
import com.amazonaws.services.neptune.util.TransferManagerWrapper;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.ObjectTagging;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static com.amazonaws.services.neptune.export.NeptuneExportService.TAGS;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ExportToS3NeptuneExportEventHandler implements NeptuneExportEventHandler {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ExportToS3NeptuneExportEventHandler.class);

    private final String localOutputPath;
    private final String outputS3Path;
    private final String completionFileS3Path;
    private final ObjectNode completionFilePayload;
    private final AtomicReference<S3ObjectInfo> result = new AtomicReference<>();

    public ExportToS3NeptuneExportEventHandler(String localOutputPath,
                                               String outputS3Path,
                                               String completionFileS3Path,
                                               ObjectNode completionFilePayload) {
        this.localOutputPath = localOutputPath;
        this.outputS3Path = outputS3Path;
        this.completionFileS3Path = completionFileS3Path;
        this.completionFilePayload = completionFilePayload;
    }

    @Override
    public void onExportComplete(Path outputPath, ExportStats stats) throws Exception {

        try (TransferManagerWrapper transferManager = new TransferManagerWrapper()) {

            File outputDirectory = outputPath.toFile();
            S3ObjectInfo outputS3ObjectInfo = calculateOutputS3Path(outputDirectory);

            Timer.timedActivity("uploading files to S3", (CheckedActivity.Runnable) () -> {
                uploadExportFilesToS3(transferManager.get(), outputDirectory, outputS3ObjectInfo);
                uploadCompletionFileToS3(transferManager.get(), outputDirectory, outputS3ObjectInfo, stats);
            });

            result.set(outputS3ObjectInfo);
        }
    }

    @Override
    public void onExportComplete(Path outputPath, ExportStats stats, GraphSchema graphSchema) throws Exception {
        onExportComplete(outputPath, stats);
    }

    public S3ObjectInfo result() {
        return result.get();
    }

    private S3ObjectInfo calculateOutputS3Path(File outputDirectory) {
        S3ObjectInfo outputBaseS3ObjectInfo = new S3ObjectInfo(outputS3Path);
        return outputBaseS3ObjectInfo.withNewKeySuffix(outputDirectory.getName());
    }

    private void uploadCompletionFileToS3(TransferManager transferManager,
                                          File directory,
                                          S3ObjectInfo outputS3ObjectInfo,
                                          ExportStats stats) throws IOException {

        if (StringUtils.isEmpty(completionFileS3Path)) {
            return;
        }

        if (directory == null || !directory.exists()) {
            logger.warn("Ignoring request to upload completion file to S3 because directory from which to upload files does not exist");
            return;
        }

        File completionFile = new File(localOutputPath, directory.getName() + ".json");

        ObjectNode neptuneExportNode = JsonNodeFactory.instance.objectNode();
        completionFilePayload.set("neptuneExport", neptuneExportNode);
        neptuneExportNode.put("outputS3Path", outputS3ObjectInfo.toString());
        stats.addTo(neptuneExportNode);

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(completionFile), UTF_8))) {
            ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
            writer.write(objectWriter.writeValueAsString(completionFilePayload));
        }

        S3ObjectInfo completionFileS3ObjectInfo =
                new S3ObjectInfo(completionFileS3Path).replaceOrAppendKey(
                        "_COMPLETION_ID_",
                        FilenameUtils.getBaseName(completionFile.getName()),
                        completionFile.getName());


        try (InputStream inputStream = new FileInputStream(completionFile)) {

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(completionFile.length());
            objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

            PutObjectRequest putObjectRequest = new PutObjectRequest(completionFileS3ObjectInfo.bucket(),
                    completionFileS3ObjectInfo.key(),
                    inputStream,
                    objectMetadata).withTagging(new ObjectTagging(TAGS));

            Upload upload = transferManager.upload(putObjectRequest);

            upload.waitForUploadResult();

        } catch (InterruptedException e) {
            logger.warn(e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void uploadExportFilesToS3(TransferManager transferManager, File directory, S3ObjectInfo outputS3ObjectInfo) {

        if (directory == null || !directory.exists()) {
            logger.warn("Ignoring request to upload files to S3 because upload directory from which to upload files does not exist");
            return;
        }

        try {

            ObjectMetadataProvider metadataProvider = (file, objectMetadata) -> {
                objectMetadata.setContentLength(file.length());
                objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            };

            ObjectTaggingProvider taggingProvider = uploadContext -> new ObjectTagging(TAGS);

            MultipleFileUpload upload = transferManager.uploadDirectory(
                    outputS3ObjectInfo.bucket(),
                    outputS3ObjectInfo.key(),
                    directory,
                    true,
                    metadataProvider,
                    taggingProvider);

            upload.waitForCompletion();
        } catch (InterruptedException e) {
            logger.warn(e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}