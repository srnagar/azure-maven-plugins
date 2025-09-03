/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.legacy.function.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.toolkit.lib.legacy.function.bindings.Binding;
import com.microsoft.azure.toolkit.lib.legacy.function.bindings.BindingEnum;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for McpAnnotationProcessor class.
 */
public class McpAnnotationProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public McpAnnotationProcessorTest() {
        // Default constructor for JUnit
    }

    @Test
    public void testProcessMcpAnnotations_WithTriggerAndProperties_ShouldSetToolProperties() throws Exception {
        // Arrange
        List<Binding> bindings = new ArrayList<>();
        
        // Create McpToolProperty bindings
        Binding property1 = createMcpToolPropertyBinding("prop1", "value1");
        Binding property2 = createMcpToolPropertyBinding("prop2", "value2");
        
        // Create McpToolTrigger binding
        Binding trigger = createMcpToolTriggerBinding("myTool");
        
        bindings.add(property1);
        bindings.add(property2);
        bindings.add(trigger);
        
        // Act
        McpAnnotationProcessor.processMcpAnnotations(bindings);
        
        // Assert
        // Check that properties are patched correctly
        assertEquals("prop1", property1.getAttribute("propertyName"));
        assertEquals("prop2", property2.getAttribute("propertyName"));
        
        // Check that trigger is patched correctly
        assertEquals("myTool", trigger.getAttribute("toolName"));
        
        // Check that toolProperties is set on trigger
        String toolPropertiesJson = (String) trigger.getAttribute("toolProperties");
        assertNotNull(toolPropertiesJson);
        
        // Parse and verify toolProperties JSON
        List<Map<String, Object>> toolProperties = OBJECT_MAPPER.readValue(
            toolPropertiesJson, 
            new TypeReference<List<Map<String, Object>>>() {}
        );
        
        assertEquals(2, toolProperties.size());
        
        // Verify first property (excluding 'name')
        Map<String, Object> prop1Map = toolProperties.get(0);
        assertEquals("prop1", prop1Map.get("propertyName"));
        assertEquals("value1", prop1Map.get("customAttribute"));
        assertFalse(prop1Map.containsKey("name")); // 'name' should be excluded
        
        // Verify second property (excluding 'name')
        Map<String, Object> prop2Map = toolProperties.get(1);
        assertEquals("prop2", prop2Map.get("propertyName"));
        assertEquals("value2", prop2Map.get("customAttribute"));
        assertFalse(prop2Map.containsKey("name")); // 'name' should be excluded
    }

    @Test
    public void testProcessMcpAnnotations_WithTriggerOnly_ShouldNotSetToolProperties() {
        // Arrange
        List<Binding> bindings = new ArrayList<>();
        Binding trigger = createMcpToolTriggerBinding("myTool");
        bindings.add(trigger);
        
        // Act
        McpAnnotationProcessor.processMcpAnnotations(bindings);
        
        // Assert
        assertEquals("myTool", trigger.getAttribute("toolName"));
        assertNull(trigger.getAttribute("toolProperties"));
    }

    @Test
    public void testProcessMcpAnnotations_WithPropertiesOnly_ShouldPatchProperties() {
        // Arrange
        List<Binding> bindings = new ArrayList<>();
        Binding property1 = createMcpToolPropertyBinding("prop1", "value1");
        Binding property2 = createMcpToolPropertyBinding("prop2", "value2");
        bindings.add(property1);
        bindings.add(property2);
        
        // Act
        McpAnnotationProcessor.processMcpAnnotations(bindings);
        
        // Assert
        assertEquals("prop1", property1.getAttribute("propertyName"));
        assertEquals("prop2", property2.getAttribute("propertyName"));
    }

    @Test
    public void testProcessMcpAnnotations_WithMultipleTriggers_ShouldSetToolPropertiesOnAll() throws Exception {
        // Arrange
        List<Binding> bindings = new ArrayList<>();
        
        Binding property1 = createMcpToolPropertyBinding("prop1", "value1");
        Binding trigger1 = createMcpToolTriggerBinding("tool1");
        Binding trigger2 = createMcpToolTriggerBinding("tool2");
        
        bindings.add(property1);
        bindings.add(trigger1);
        bindings.add(trigger2);
        
        // Act
        McpAnnotationProcessor.processMcpAnnotations(bindings);
        
        // Assert
        assertEquals("prop1", property1.getAttribute("propertyName"));
        assertEquals("tool1", trigger1.getAttribute("toolName"));
        assertEquals("tool2", trigger2.getAttribute("toolName"));
        
        // Both triggers should have the same toolProperties
        String toolProperties1 = (String) trigger1.getAttribute("toolProperties");
        String toolProperties2 = (String) trigger2.getAttribute("toolProperties");
        
        assertNotNull(toolProperties1);
        assertNotNull(toolProperties2);
        assertEquals(toolProperties1, toolProperties2);
        
        // Verify the content
        List<Map<String, Object>> properties = OBJECT_MAPPER.readValue(
            toolProperties1, 
            new TypeReference<List<Map<String, Object>>>() {}
        );
        assertEquals(1, properties.size());
        assertEquals("prop1", properties.get(0).get("propertyName"));
    }

    @Test
    public void testProcessMcpAnnotations_WithEmptyList_ShouldNotThrow() {
        // Arrange
        List<Binding> bindings = new ArrayList<>();
        
        // Act & Assert - should not throw any exception
        try {
            McpAnnotationProcessor.processMcpAnnotations(bindings);
            // If we get here, no exception was thrown - test passes
        } catch (Exception e) {
            fail("Expected no exception to be thrown, but got: " + e.getMessage());
        }
    }

    @Test
    public void testProcessMcpAnnotations_WithOtherBindingTypes_ShouldIgnoreThem() {
        // Arrange
        List<Binding> bindings = new ArrayList<>();
        
        // Add non-MCP bindings
        Binding httpTrigger = new Binding(BindingEnum.HttpTrigger);
        httpTrigger.setAttribute("name", "req");
        
        Binding blobInput = new Binding(BindingEnum.BlobInput);
        blobInput.setAttribute("name", "input");
        
        // Add MCP bindings
        Binding mcpProperty = createMcpToolPropertyBinding("prop1", "value1");
        Binding mcpTrigger = createMcpToolTriggerBinding("myTool");
        
        bindings.add(httpTrigger);
        bindings.add(mcpProperty);
        bindings.add(blobInput);
        bindings.add(mcpTrigger);
        
        // Act
        McpAnnotationProcessor.processMcpAnnotations(bindings);
        
        // Assert
        // Non-MCP bindings should be unchanged
        assertEquals("req", httpTrigger.getAttribute("name"));
        assertEquals("input", blobInput.getAttribute("name"));
        assertNull(httpTrigger.getAttribute("toolName"));
        assertNull(blobInput.getAttribute("toolName"));
        
        // MCP bindings should be processed
        assertEquals("prop1", mcpProperty.getAttribute("propertyName"));
        assertEquals("myTool", mcpTrigger.getAttribute("toolName"));
        assertNotNull(mcpTrigger.getAttribute("toolProperties"));
    }

    @Test
    public void testProcessMcpAnnotations_WithNullName_ShouldSkipPatching() {
        // Arrange
        List<Binding> bindings = new ArrayList<>();
        
        Binding propertyWithNullName = new Binding(BindingEnum.McpToolProperty);
        propertyWithNullName.setAttribute("name", null);
        propertyWithNullName.setAttribute("customAttribute", "value");
        
        Binding triggerWithNullName = new Binding(BindingEnum.McpToolTrigger);
        triggerWithNullName.setAttribute("name", null);
        
        bindings.add(propertyWithNullName);
        bindings.add(triggerWithNullName);
        
        // Act
        McpAnnotationProcessor.processMcpAnnotations(bindings);
        
        // Assert
        // Individual patching should be skipped due to null name
        assertNull(propertyWithNullName.getAttribute("propertyName"));
        assertNull(triggerWithNullName.getAttribute("toolName"));
        
        // But toolProperties should still be set since the property exists
        assertNotNull(triggerWithNullName.getAttribute("toolProperties"));
    }

    @Test
    public void testProcessMcpAnnotations_WithEmptyName_ShouldSkipPatching() {
        // Arrange
        List<Binding> bindings = new ArrayList<>();
        
        Binding propertyWithEmptyName = new Binding(BindingEnum.McpToolProperty);
        propertyWithEmptyName.setAttribute("name", "");
        propertyWithEmptyName.setAttribute("customAttribute", "value");
        
        Binding triggerWithEmptyName = new Binding(BindingEnum.McpToolTrigger);
        triggerWithEmptyName.setAttribute("name", "");
        
        bindings.add(propertyWithEmptyName);
        bindings.add(triggerWithEmptyName);
        
        // Act
        McpAnnotationProcessor.processMcpAnnotations(bindings);
        
        // Assert
        // Individual patching should be skipped due to empty name
        assertNull(propertyWithEmptyName.getAttribute("propertyName"));
        assertNull(triggerWithEmptyName.getAttribute("toolName"));
        
        // But toolProperties should still be set since the property exists
        assertNotNull(triggerWithEmptyName.getAttribute("toolProperties"));
    }

    @Test
    public void testProcessMcpAnnotations_OrderIndependent_ShouldWork() throws Exception {
        // Arrange - trigger before properties
        List<Binding> bindings = new ArrayList<>();
        
        Binding trigger = createMcpToolTriggerBinding("myTool");
        Binding property1 = createMcpToolPropertyBinding("prop1", "value1");
        Binding property2 = createMcpToolPropertyBinding("prop2", "value2");
        
        // Add trigger first, then properties
        bindings.add(trigger);
        bindings.add(property1);
        bindings.add(property2);
        
        // Act
        McpAnnotationProcessor.processMcpAnnotations(bindings);
        
        // Assert - should work regardless of order
        assertEquals("myTool", trigger.getAttribute("toolName"));
        assertEquals("prop1", property1.getAttribute("propertyName"));
        assertEquals("prop2", property2.getAttribute("propertyName"));
        
        String toolPropertiesJson = (String) trigger.getAttribute("toolProperties");
        assertNotNull(toolPropertiesJson);
        
        List<Map<String, Object>> toolProperties = OBJECT_MAPPER.readValue(
            toolPropertiesJson, 
            new TypeReference<List<Map<String, Object>>>() {}
        );
        assertEquals(2, toolProperties.size());
    }

    // Helper methods to create test bindings

    private Binding createMcpToolPropertyBinding(String name, String customValue) {
        Binding binding = new Binding(BindingEnum.McpToolProperty);
        binding.setAttribute("name", name);
        binding.setAttribute("customAttribute", customValue);
        return binding;
    }

    private Binding createMcpToolTriggerBinding(String name) {
        Binding binding = new Binding(BindingEnum.McpToolTrigger);
        binding.setAttribute("name", name);
        return binding;
    }
}
