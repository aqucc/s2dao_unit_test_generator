package java.sql;
public interface DatabaseMetaData extends Wrapper {
  int procedureColumnUnknown=0; int procedureColumnIn=1; int procedureColumnInOut=2;
  int procedureColumnResult=3; int procedureColumnOut=4; int procedureColumnReturn=5;
  int procedureNoResult=1; int procedureReturnsResult=2; int procedureResultUnknown=0;
  int procedureNoNulls=0; int procedureNullable=1; int procedureNullableUnknown=2;
  ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException;
  ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException;

  String getDatabaseProductName() throws SQLException;
  String getDatabaseProductVersion() throws SQLException;
  int getDatabaseMajorVersion() throws SQLException;
  int getDatabaseMinorVersion() throws SQLException;
  String getURL() throws SQLException;
  String getUserName() throws SQLException;
  String getDriverName() throws SQLException;
  ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException;
  ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException;
  ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException;
  String getIdentifierQuoteString() throws SQLException;
  boolean storesUpperCaseIdentifiers() throws SQLException;
  boolean storesLowerCaseIdentifiers() throws SQLException;
  boolean storesMixedCaseIdentifiers() throws SQLException;
  boolean supportsMixedCaseIdentifiers() throws SQLException;
  boolean supportsSchemasInTableDefinitions() throws SQLException;
  boolean supportsGetGeneratedKeys() throws SQLException;
  Connection getConnection() throws SQLException;
}
