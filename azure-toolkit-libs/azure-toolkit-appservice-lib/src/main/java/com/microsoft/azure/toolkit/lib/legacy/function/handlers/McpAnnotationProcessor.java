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
     * This performs patching of individual bindings and generation of toolProperties in a single pass
     * for optimal performance.
     * 
     * Note: Duplicate name validation is now handled at the function level by AnnotationHandlerImpl.
     * 
     * Assumes each method has at most one McpToolTrigger that receives all McpToolProperty data.
     * 
     * @param bindings the list of bindings to update
     */
    public static void processMcpAnnotations(final List<Binding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        
        final List<Map<String, Object>> allProperties = new ArrayList<>();
        final List<Binding> mcpTriggers = new ArrayList<>();
        
        // Single pass: Process all bindings and categorize them
        for (final Binding binding : bindings) {
            final BindingEnum bindingType = binding.getBindingEnum();
            
            if (bindingType == BindingEnum.McpToolProperty) {
                processPropertyBinding(binding, allProperties);
            } else if (bindingType == BindingEnum.McpToolTrigger) {
                patchMcpToolTrigger(binding);
                mcpTriggers.add(binding);
            }
        }
        
        // Apply toolProperties to all triggers (only generate JSON once if needed)
        if (!allProperties.isEmpty() && !mcpTriggers.isEmpty()) {
            final String toolPropertiesJson = JsonUtils.toJson(allProperties);
            for (final Binding trigger : mcpTriggers) {
                trigger.setAttribute("toolProperties", toolPropertiesJson);
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
        if (isValidPropertyName(name)) {
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
        if (isValidPropertyName(name)) {
            binding.setAttribute("propertyName", name);
        }
    }

    /**
     * Processes a single McpToolProperty binding: patches it and adds its attributes 
     * to the properties collection.
     * 
     * @param binding the property binding to process
     * @param allProperties the collection to add processed attributes to
     */
    private static void processPropertyBinding(final Binding binding, 
                                               final List<Map<String, Object>> allProperties) {
        patchMcpToolProperty(binding);
        
        // Create filtered attributes map (excluding 'name' for toolProperties)
        final Map<String, Object> propertyAttributes = createFilteredAttributesMap(binding);
        allProperties.add(propertyAttributes);
    }

    /**
     * Creates a filtered map of binding attributes, excluding the 'name' attribute.
     * 
     * @param binding the binding to extract attributes from
     * @return a new map with all attributes except 'name'
     */
    private static Map<String, Object> createFilteredAttributesMap(final Binding binding) {
        final Map<String, Object> propertyAttributes = new HashMap<>();
        final Map<String, Object> bindingAttributes = binding.getBindingAttributes();
        
        for (final Map.Entry<String, Object> entry : bindingAttributes.entrySet()) {
            if (!"name".equals(entry.getKey())) {
                propertyAttributes.put(entry.getKey(), entry.getValue());
            }
        }
        
        return propertyAttributes;
    }

    /**
     * Checks if a property name is valid (non-null and non-empty).
     * 
     * @param propertyName the property name to validate
     * @return true if the property name is valid
     */
    private static boolean isValidPropertyName(final String propertyName) {
        return propertyName != null && !propertyName.isEmpty();
    }
}
