/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.legacy.function.handlers;

import com.microsoft.azure.toolkit.lib.common.utils.JsonUtils;
import com.microsoft.azure.toolkit.lib.legacy.function.bindings.Binding;
import com.microsoft.azure.toolkit.lib.legacy.function.bindings.BindingEnum;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.microsoft.azure.toolkit.lib.appservice.function.core.AzureFunctionsAnnotationConstants.MCP_TOOL_PROPERTY;
import static com.microsoft.azure.toolkit.lib.appservice.function.core.AzureFunctionsAnnotationConstants.MCP_TOOL_TRIGGER;

/**
 * Processor for handling MCP (Model Context Protocol) annotations in Azure Functions.
 * This class is responsible for processing McpToolTrigger and McpToolProperty annotations
 * and generating the appropriate binding configurations.
 */
public class McpAnnotationProcessor {

    /**
     * Processes all MCP-related annotations for a given method and updates the bindings accordingly.
     * This performs patching of individual bindings and generation of toolProperties in two passes:
     * 1. First processes all McpToolProperty bindings to collect their attributes
     * 2. Then processes all McpToolTrigger bindings and sets their toolProperties
     *
     * @param method the method to process
     * @param bindings the list of bindings to update
     */
    public void processMcpAnnotations(final Method method, final List<Binding> bindings) {
        // First pass: Process ALL McpToolProperty bindings and collect their attributes
        final List<Map<String, Object>> allProperties = new ArrayList<>();
        for (final Binding binding : bindings) {
            if (binding.getBindingEnum() == BindingEnum.McpToolProperty) {
                patchMcpToolPropertyFromMethod(method, binding);
                
                // Create a filtered map excluding 'name' attribute for toolProperties
                final Map<String, Object> propertyAttributes = new HashMap<>();
                final Map<String, Object> bindingAttributes = binding.getBindingAttributes();
                for (Map.Entry<String, Object> entry : bindingAttributes.entrySet()) {
                    if (!"name".equals(entry.getKey())) {
                        propertyAttributes.put(entry.getKey(), entry.getValue());
                    }
                }
                allProperties.add(propertyAttributes);
            }
        }
        
        // Second pass: Process ALL McpToolTrigger bindings and set their toolProperties
        for (final Binding binding : bindings) {
            if (binding.getBindingEnum() == BindingEnum.McpToolTrigger) {
                patchMcpToolTriggerFromMethod(method, binding);
                
                if (!allProperties.isEmpty()) {
                    final String toolPropertiesJson = JsonUtils.toJson(allProperties);
                    binding.setAttribute("toolProperties", toolPropertiesJson);
                }
            }
        }
    }

    /**
     * Patches McpToolTrigger binding by finding the corresponding parameter and extracting attributes.
     */
    private void patchMcpToolTriggerFromMethod(final Method method, final Binding binding) {
        final Parameter param = findParameterForBinding(method, binding, MCP_TOOL_TRIGGER);
        if (param != null) {
            patchMcpToolTrigger(param, binding);
        }
    }

    /**
     * Patches McpToolProperty binding by finding the corresponding parameter and extracting attributes.
     */
    private void patchMcpToolPropertyFromMethod(final Method method, final Binding binding) {
        final Parameter param = findParameterForBinding(method, binding, MCP_TOOL_PROPERTY);
        if (param != null) {
            patchMcpToolProperty(param, binding);
        }
    }

    /**
     * Finds the parameter that corresponds to a binding by checking annotations.
     */
    private Parameter findParameterForBinding(final Method method, final Binding binding, final String expectedAnnotationType) {
        for (final Parameter param : method.getParameters()) {
            for (final Annotation annotation : param.getAnnotations()) {
                if (expectedAnnotationType.equals(annotation.annotationType().getName())) {
                    // Check if this annotation would create the same type of binding
                    if ((MCP_TOOL_TRIGGER.equals(expectedAnnotationType) && binding.getBindingEnum() == BindingEnum.McpToolTrigger) ||
                        (MCP_TOOL_PROPERTY.equals(expectedAnnotationType) && binding.getBindingEnum() == BindingEnum.McpToolProperty)) {
                        // Additional check: if the binding already has a name, it should match the annotation's name
                        final String bindingName = binding.getName();
                        if (bindingName != null) {
                            final String annotationName = getAnnotationAttribute(param, expectedAnnotationType, "name");
                            if (bindingName.equals(annotationName)) {
                                return param;
                            }
                        } else {
                            // If binding has no name yet, this could be the right parameter
                            return param;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Patches McpToolTrigger binding with toolName extracted from the 'name' attribute.
     */
    private void patchMcpToolTrigger(final Parameter param, final Binding binding) {
        final String name = getAnnotationAttribute(param, MCP_TOOL_TRIGGER, "name");
        if (name != null && !name.isEmpty()) {
            binding.setAttribute("toolName", name);
        }
    }

    /**
     * Patches McpToolProperty binding with propertyName extracted from the 'name' attribute.
     */
    private void patchMcpToolProperty(final Parameter param, final Binding binding) {
        final String name = getAnnotationAttribute(param, MCP_TOOL_PROPERTY, "name");
        if (name != null && !name.isEmpty()) {
            binding.setAttribute("propertyName", name);
        }
    }

    /**
     * Safely extracts an attribute value from an annotation on a parameter.
     */
    private String getAnnotationAttribute(final Parameter param, final String annotationType, final String attributeName) {
        try {
            for (final Annotation annotation : param.getAnnotations()) {
                if (annotationType.equals(annotation.annotationType().getName())) {
                    final Method method = annotation.annotationType().getMethod(attributeName);
                    final Object value = method.invoke(annotation);
                    return value != null ? value.toString() : null;
                }
            }
        } catch (final Exception e) {
            // Log warning but don't fail the build
            System.err.println("Warning: Failed to extract annotation attribute '" + attributeName + 
                             "' from " + annotationType + ": " + e.getMessage());
        }
        return null;
    }
}
