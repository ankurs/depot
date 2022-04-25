package io.odpf.sink.connectors.bigquery.proto;

import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.protobuf.Descriptors.Descriptor;
import io.odpf.sink.connectors.bigquery.converter.MessageRecordConverter;
import io.odpf.sink.connectors.bigquery.converter.MessageRecordConverterCache;
import io.odpf.sink.connectors.bigquery.exception.BQSchemaMappingException;
import io.odpf.sink.connectors.bigquery.exception.BQTableUpdateFailure;
import io.odpf.sink.connectors.bigquery.handler.BigQueryClient;
import io.odpf.sink.connectors.message.InputSchemaMessageMode;
import io.odpf.sink.connectors.message.OdpfMessageSchema;
import io.odpf.sink.connectors.message.proto.ProtoField;
import io.odpf.sink.connectors.config.BigQuerySinkConfig;
import io.odpf.sink.connectors.common.TupleString;
import io.odpf.sink.connectors.message.proto.ProtoOdpfMessageSchema;
import io.odpf.sink.connectors.stencil.OdpfStencilUpdateListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class BigqueryProtoUpdateListener extends OdpfStencilUpdateListener {
    private final BigQuerySinkConfig config;
    private final BigQueryClient bqClient;
    @Getter
    private final MessageRecordConverterCache recordConverterWrapper;

    public BigqueryProtoUpdateListener(BigQuerySinkConfig config, BigQueryClient bqClient, MessageRecordConverterCache recordConverterWrapper) {
        this.config = config;
        this.bqClient = bqClient;
        this.recordConverterWrapper = recordConverterWrapper;
    }

    @Override
    public void onSchemaUpdate(Map<String, Descriptor> newDescriptors) {
        log.info("stencil cache was refreshed, validating if bigquery schema changed");
        try {
            InputSchemaMessageMode mode = config.getSinkConnectorSchemaMessageMode();
            String schemaClass = mode == InputSchemaMessageMode.LOG_MESSAGE
                    ? config.getSinkConnectorSchemaMessageClass() : config.getSinkConnectorSchemaKeyClass();
            OdpfMessageSchema schema = getOdpfMessageParser().getSchema(schemaClass);
            ProtoField protoField = ((ProtoOdpfMessageSchema) schema).getProtoField();
            List<Field> bqSchemaFields = ProtoUtils.generateBigquerySchema(protoField);
            addMetadataFields(bqSchemaFields);
            bqClient.upsertTable(bqSchemaFields);
            recordConverterWrapper.setMessageRecordConverter(new MessageRecordConverter(getOdpfMessageParser(), config, schema));
        } catch (BigQueryException | IOException e) {
            String errMsg = "Error while updating bigquery table on callback:" + e.getMessage();
            log.error(errMsg);
            throw new BQTableUpdateFailure(errMsg, e);
        }
    }

    private void addMetadataFields(List<Field> bqSchemaFields) {
        List<Field> bqMetadataFields = new ArrayList<>();
        String namespaceName = config.getBqMetadataNamespace();
        if (config.shouldAddMetadata()) {
            List<TupleString> metadataColumnsTypes = config.getMetadataColumnsTypes();
            if (namespaceName.isEmpty()) {
                bqMetadataFields.addAll(ProtoUtils.getMetadataFields(metadataColumnsTypes));
            } else {
                bqMetadataFields.add(ProtoUtils.getNamespacedMetadataField(namespaceName, metadataColumnsTypes));
            }
        }

        List<String> duplicateFields = getDuplicateFields(bqSchemaFields, bqMetadataFields).stream().map(Field::getName).collect(Collectors.toList());
        if (duplicateFields.size() > 0) {
            throw new BQSchemaMappingException(String.format("Metadata field(s) is already present in the schema. "
                    + "fields: %s", duplicateFields));
        }
        bqSchemaFields.addAll(bqMetadataFields);
    }

    public void close() throws IOException {
    }

    private List<Field> getDuplicateFields(List<Field> fields1, List<Field> fields2) {
        return fields1.stream().filter(field -> containsField(fields2, field.getName())).collect(Collectors.toList());
    }

    private boolean containsField(List<Field> fields, String fieldName) {
        return fields.stream().anyMatch(field -> field.getName().equals(fieldName));
    }

}
