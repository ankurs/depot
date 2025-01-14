package com.gotocompany.depot.common;

import com.google.common.base.Splitter;
import com.gotocompany.depot.exception.InvalidTemplateException;
import com.gotocompany.depot.message.MessageSchema;
import com.gotocompany.depot.message.ParsedMessage;
import com.gotocompany.depot.message.field.GenericFieldFactory;
import com.gotocompany.depot.message.proto.converter.fields.ProtoField;
import com.gotocompany.depot.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class Template {
    private final String templatePattern;
    private final List<String> patternVariableFieldNames;

    public Template(String template) throws InvalidTemplateException {
        if (template == null || template.isEmpty()) {
            throw new InvalidTemplateException("Template cannot be empty");
        }
        List<String> templateStrings = new ArrayList<>();
        Splitter.on(",").omitEmptyStrings().split(template).forEach(s -> templateStrings.add(s.trim()));
        this.templatePattern = templateStrings.get(0);
        this.patternVariableFieldNames = templateStrings.subList(1, templateStrings.size());
        validate();
    }

    private void validate() throws InvalidTemplateException {
        int validArgs = StringUtils.countVariables(templatePattern);
        int values = patternVariableFieldNames.size();
        int variables = StringUtils.count(templatePattern, '%');
        if (validArgs != values || variables != values) {
            throw new InvalidTemplateException(String.format("Template is not valid, variables=%d, validArgs=%d, values=%d", variables, validArgs, values));
        }
    }

    public String parse(ParsedMessage parsedMessage, MessageSchema schema) {
        Object[] patternVariableData = patternVariableFieldNames
                .stream()
                .map(fieldName -> fetchInternalValue(parsedMessage.getFieldByName(fieldName, schema)))
                .toArray();
        return String.format(templatePattern, patternVariableData);
    }

    private Object fetchInternalValue(Object ob) {
        if (ob instanceof ProtoField) {
            return GenericFieldFactory.getField(ob).getString();
        } else {
            return ob;
        }
    }
}
