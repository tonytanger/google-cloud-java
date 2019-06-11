// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/cloud/bigquery/datatransfer/v1/datasource.proto

package com.google.cloud.bigquery.datatransfer.v1;

public interface CreateDataSourceDefinitionRequestOrBuilder
    extends
    // @@protoc_insertion_point(interface_extends:google.cloud.bigquery.datatransfer.v1.CreateDataSourceDefinitionRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   *
   *
   * <pre>
   * The BigQuery project id for which data source definition is associated.
   * Must be in the form: `projects/{project_id}/locations/{location_id}`
   * </pre>
   *
   * <code>string parent = 1;</code>
   */
  java.lang.String getParent();
  /**
   *
   *
   * <pre>
   * The BigQuery project id for which data source definition is associated.
   * Must be in the form: `projects/{project_id}/locations/{location_id}`
   * </pre>
   *
   * <code>string parent = 1;</code>
   */
  com.google.protobuf.ByteString getParentBytes();

  /**
   *
   *
   * <pre>
   * Data source definition.
   * </pre>
   *
   * <code>.google.cloud.bigquery.datatransfer.v1.DataSourceDefinition data_source_definition = 2;
   * </code>
   */
  boolean hasDataSourceDefinition();
  /**
   *
   *
   * <pre>
   * Data source definition.
   * </pre>
   *
   * <code>.google.cloud.bigquery.datatransfer.v1.DataSourceDefinition data_source_definition = 2;
   * </code>
   */
  com.google.cloud.bigquery.datatransfer.v1.DataSourceDefinition getDataSourceDefinition();
  /**
   *
   *
   * <pre>
   * Data source definition.
   * </pre>
   *
   * <code>.google.cloud.bigquery.datatransfer.v1.DataSourceDefinition data_source_definition = 2;
   * </code>
   */
  com.google.cloud.bigquery.datatransfer.v1.DataSourceDefinitionOrBuilder
      getDataSourceDefinitionOrBuilder();
}
