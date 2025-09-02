/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.legacy.function.handlers;

import com.microsoft.azure.toolkit.lib.common.utils.JsonUtils;
import com.microsoft.azure.toolkit.lib.legacy.function.bindings.Binding;
import com.microsoft.azure.toolkit.lib.legacy.function.bindings.BindingEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processor for handling MCP (Model Context Protocol) annotations in Azure Functions.
 * This class is responsible for processing McpToolTrigger and McpToolProperty annotations
 * and generating the appropriate binding configurations for function.json.
 * 
 * McpToolTrigger annotations define tool invocation triggers with a toolName.
 * McpToolProperty annotations define tool properties that are aggregated into toolProperties JSON.
 */
public class McpAnnotationProcessor {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private McpAnnotationProcessor() {
        // Utility class - no instances allowed
    }

    /**
     * Processes all MCP-related annotations and updates the bindings accordingly.
     * This performs patching of individual bindings and generation of toolProperties in two passes:
     * 1. First processes all McpToolProperty bindings to collect their attributes (excluding 'name')
     * 2. Then processes all McpToolTrigger bindings and sets their toolProperties with collected data
     * 
     * Assumes each method has at most one McpToolTrigger that receives all McpToolProperty data.
     *
     * @param bindings the list of bindings to update
     */
    public static void processMcpAnnotations(final List<Binding> bindings) {
        // First pass: Process ALL McpToolProperty bindings and collect their attributes
        final List<Map<String, Object>> allProperties = new ArrayList<>();
        for (final Binding binding : bindings) {
            if (binding.getBindingEnum() == BindingEnum.McpToolProperty) {
                patchMcpToolProperty(binding);
                
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
                patchMcpToolTrigger(binding);
                
                if (!allProperties.isEmpty()) {
                    final String toolPropertiesJson = JsonUtils.toJson(allProperties);
                    binding.setAttribute("toolProperties", toolPropertiesJson);
                }
            }
        }
    }

    /**
     * Extracts the 'name' attribute from an McpToolTrigger binding and sets it as 'toolName' 
     * on the binding for function.json generation.
     * 
     * @param binding the binding to update
     */
    private static void patchMcpToolTrigger(final Binding binding) {
        final String name = (String) binding.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            binding.setAttribute("toolName", name);
        }
    }

    /**
     * Extracts the 'name' attribute from an McpToolProperty binding and sets it as 'propertyName' 
     * on the binding. Note: Does NOT set 'toolName' on property bindings.
     * 
     * @param binding the binding to update
     */
    private static void patchMcpToolProperty(final Binding binding) {
        final String name = (String) binding.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            binding.setAttribute("propertyName", name);
        }
    }
}
