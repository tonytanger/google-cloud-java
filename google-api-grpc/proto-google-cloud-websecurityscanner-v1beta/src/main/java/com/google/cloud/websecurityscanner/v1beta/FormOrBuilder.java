// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/cloud/websecurityscanner/v1beta/finding_addon.proto

package com.google.cloud.websecurityscanner.v1beta;

public interface FormOrBuilder
    extends
    // @@protoc_insertion_point(interface_extends:google.cloud.websecurityscanner.v1beta.Form)
    com.google.protobuf.MessageOrBuilder {

  /**
   *
   *
   * <pre>
   * ! The URI where to send the form when it's submitted.
   * </pre>
   *
   * <code>string action_uri = 1;</code>
   */
  java.lang.String getActionUri();
  /**
   *
   *
   * <pre>
   * ! The URI where to send the form when it's submitted.
   * </pre>
   *
   * <code>string action_uri = 1;</code>
   */
  com.google.protobuf.ByteString getActionUriBytes();

  /**
   *
   *
   * <pre>
   * ! The names of form fields related to the vulnerability.
   * </pre>
   *
   * <code>repeated string fields = 2;</code>
   */
  java.util.List<java.lang.String> getFieldsList();
  /**
   *
   *
   * <pre>
   * ! The names of form fields related to the vulnerability.
   * </pre>
   *
   * <code>repeated string fields = 2;</code>
   */
  int getFieldsCount();
  /**
   *
   *
   * <pre>
   * ! The names of form fields related to the vulnerability.
   * </pre>
   *
   * <code>repeated string fields = 2;</code>
   */
  java.lang.String getFields(int index);
  /**
   *
   *
   * <pre>
   * ! The names of form fields related to the vulnerability.
   * </pre>
   *
   * <code>repeated string fields = 2;</code>
   */
  com.google.protobuf.ByteString getFieldsBytes(int index);
}
