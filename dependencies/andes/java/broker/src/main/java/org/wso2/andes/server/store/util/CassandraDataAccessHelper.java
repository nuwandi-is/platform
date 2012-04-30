/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.andes.server.store.util;


import me.prettyprint.cassandra.serializers.*;
import me.prettyprint.cassandra.service.CassandraHostConfigurator;
import me.prettyprint.cassandra.service.ThriftCfDef;
import me.prettyprint.cassandra.service.ThriftKsDef;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.ColumnSlice;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.ddl.ColumnFamilyDefinition;
import me.prettyprint.hector.api.ddl.ComparatorType;
import me.prettyprint.hector.api.ddl.KeyspaceDefinition;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.mutation.Mutator;
import me.prettyprint.hector.api.query.QueryResult;
import me.prettyprint.hector.api.query.SliceQuery;
import org.wso2.andes.server.store.CassandraConsistencyLevelPolicy;

import java.util.*;

/**
 * Class <code>CassandraDataAccessHelper</code> Encapsulate the Cassandra DataAccessLogic used in
 * CassandraMessageStore
 */
public class CassandraDataAccessHelper {


    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";


    /**
     * Serializes used for Cassandra data operations
     */
    private static StringSerializer stringSerializer = StringSerializer.get();

    private static LongSerializer longSerializer = LongSerializer.get();

    private static BytesArraySerializer bytesArraySerializer = BytesArraySerializer.get();

    private static IntegerSerializer integerSerializer = IntegerSerializer.get();

    private static ByteBufferSerializer byteBufferSerializer = ByteBufferSerializer.get();


    /**
     * Create a Cassandra Cluster instance given the connection details
     * @param userName  userName to connect to the cassandra
     * @param password  password to connect to cassandra
     * @param clusterName Cluster name
     * @param connectionString cassandra connection string
     * @return  Cluster instance
     * @throws CassandraDataAccessException   In case of and error in accessing database or data error
     */
    public static Cluster createCluster(String userName, String password, String clusterName,
                                         String connectionString) throws CassandraDataAccessException {

        if(userName == null || password == null) {
            throw new CassandraDataAccessException("Can't create cluster with empty userName or Password");
        }

        if(clusterName == null) {
            throw new CassandraDataAccessException("Can't create cluster with empty cluster name");
        }

        if(connectionString == null) {
            throw new CassandraDataAccessException("Can't create cluster with empty connection string");
        }

        Map<String, String> credentials = new HashMap<String, String>();
        credentials.put(USERNAME_KEY, userName);
        credentials.put(PASSWORD_KEY, password);

        CassandraHostConfigurator hostConfigurator =
                new CassandraHostConfigurator(connectionString);
        hostConfigurator.setMaxActive(2000);
        Cluster cluster = HFactory.getCluster(clusterName);

        if (cluster == null) {
            cluster = HFactory.createCluster(clusterName, hostConfigurator, credentials);
        }
        return cluster;
    }

    /**
     * Create a Column family in a Given Cluster instance
     * @param name  ColumnFamily Name
     * @param keySpace KeySpace name
     * @param cluster   Cluster instance
     * @param comparatorType Comparator
     * @throws CassandraDataAccessException   In case of an Error accessing database or data error
     */
    public static void createColumnFamily(String name, String keySpace, Cluster cluster, String comparatorType) throws CassandraDataAccessException {

        KeyspaceDefinition ksDef = cluster.describeKeyspace(keySpace);

        if (ksDef == null) {
            throw new CassandraDataAccessException("Can't create Column family, keyspace " + keySpace +
                    " does not exist");
        }

        ColumnFamilyDefinition cfDef =
                new ThriftCfDef(keySpace, /*"Queue"*/name,
                        ComparatorType.getByClassName(comparatorType));

        List<ColumnFamilyDefinition> cfDefsList = ksDef.getCfDefs();
        HashSet<String> cfNames = new HashSet<String>();
        for (ColumnFamilyDefinition columnFamilyDefinition : cfDefsList) {
            cfNames.add(columnFamilyDefinition.getName());
        }
        if (!cfNames.contains(name)) {
            cluster.addColumnFamily(cfDef);
        }


    }


    public static Keyspace createKeySpace(Cluster cluster , String keySpace) {

        Keyspace keyspace;

        //Define the keySpace
        KeyspaceDefinition definition = new ThriftKsDef(keySpace);

        KeyspaceDefinition def = cluster.describeKeyspace(keySpace);
        if (def == null) {
            //Adding keySpace to the cluster
            cluster.addKeyspace(definition);
        }

        keyspace = HFactory.createKeyspace(keySpace, cluster);
        CassandraConsistencyLevelPolicy policy = new CassandraConsistencyLevelPolicy();
        keyspace.setConsistencyLevelPolicy(policy);
        return keyspace;
    }

    /**
     * Get List of Strings in a Given ROW in a Cassandra Column Family
     * Here we assume that the columns in a given row have string data and key and value in the given column
     * in that row have same values.
     * @param columnFamilyName Name of the column Family
     * @param rowName  Row name
     * @param keyspace  keySpace
     * @return  List of string in that given row.
     * @throws CassandraDataAccessException   In case of database access error or data error
     */
    public static List<String> getRowList(String columnFamilyName, String rowName,Keyspace keyspace) throws CassandraDataAccessException {
        ArrayList<String> rowList = new ArrayList<String>();

        if (keyspace == null) {
            throw new CassandraDataAccessException("Can't access Data , no keyspace provided ");
        }

        if(columnFamilyName == null || rowName == null) {
            throw new CassandraDataAccessException("Can't access data with columnFamily =" + columnFamilyName +
                    " and rowName=" + rowName);
        }

        try {
            SliceQuery<String, String, String> sliceQuery = HFactory.createSliceQuery(keyspace, stringSerializer,
                    stringSerializer, stringSerializer);
            sliceQuery.setKey(rowName);
            sliceQuery.setColumnFamily(columnFamilyName);
            sliceQuery.setRange("", "", false, 10000);

            QueryResult<ColumnSlice<String, String>> result = sliceQuery.execute();
            ColumnSlice<String, String> columnSlice = result.get();
            for (HColumn<String, String> column : columnSlice.getColumns()) {
                rowList.add(column.getName());
            }
        } catch (Exception e) {
            throw new CassandraDataAccessException("Error while accessing data from :" + columnFamilyName ,e);
        }
        return rowList;
    }

    /**
     * Get set of messages in a column family
     * @param queueName QueueName
     * @param columnFamilyName ColumnFamilyName
     * @param keyspace  Cassandra KeySpace
     * @param count  max message count limit
     * @return  ColumnSlice which contain the messages
     * @throws CassandraDataAccessException
     */
    public static ColumnSlice<String, byte[]> getMessagesFromQueue(String queueName,
                                                            String columnFamilyName, Keyspace keyspace,
                                                            int count) throws CassandraDataAccessException {
        if (keyspace == null) {
            throw new CassandraDataAccessException("Can't access Data , no keyspace provided ");
        }

        if(columnFamilyName == null || queueName == null) {
            throw new CassandraDataAccessException("Can't access data with columnFamily = " + columnFamilyName +
                    " and queueName=" + queueName);
        }

        try {
            SliceQuery<String, String, byte[]> sliceQuery =
                    HFactory.createSliceQuery(keyspace, stringSerializer, stringSerializer, bytesArraySerializer);
            sliceQuery.setKey(queueName);
            sliceQuery.setRange("", "", false, count);
            sliceQuery.setColumnFamily(columnFamilyName);


            QueryResult<ColumnSlice<String, byte[]>> result = sliceQuery.execute();
            ColumnSlice<String, byte[]> columnSlice = result.get();

            return columnSlice;
        } catch (Exception e) {
            throw new CassandraDataAccessException("Error while getting data from " + columnFamilyName);
        }
    }


    /**
     * Get Number of <String,String> type columns in a given row in a cassandra column family
     * @param rowName row Name we are querying for
     * @param columnFamilyName columnFamilName
     * @param keyspace
     * @param count
     * @return
     */
    public static ColumnSlice<String, String> getStringTypeColumnsInARow(String rowName,
                                                                         String columnFamilyName,
                                                                         Keyspace keyspace,int count)
            throws CassandraDataAccessException {

        if (keyspace == null) {
            throw new CassandraDataAccessException("Can't access Data , no keyspace provided ");
        }

        if (columnFamilyName == null || rowName == null) {
            throw new CassandraDataAccessException("Can't access data with columnFamily = " + columnFamilyName +
                    " and rowName=" + rowName);
        }


        try {
            SliceQuery sliceQuery = HFactory.createSliceQuery(keyspace, stringSerializer,
                    stringSerializer, stringSerializer);
            sliceQuery.setKey(rowName);
            sliceQuery.setColumnFamily(columnFamilyName);
            sliceQuery.setRange("", "", false, count);

            QueryResult<ColumnSlice<String, String>> result = sliceQuery.execute();
            ColumnSlice<String, String> columnSlice = result.get();

            return columnSlice;
        } catch (Exception e) {
            throw new CassandraDataAccessException("Error while getting data from : " + columnFamilyName);
        }
    }


    /**
     * Add Message to a Given Queue in Cassandra
     * @param columnFamily ColumnFamily name
     * @param queue  queue name
     * @param messageId  Message id
     * @param message  message in bytes
     * @param keyspace  Cassandra KeySpace
     * @throws CassandraDataAccessException  In case of database access error
     */
    public static void addMessageToQueue(String columnFamily , String queue , String messageId ,
                                         byte []message , Keyspace keyspace) throws CassandraDataAccessException {
        try {
            Mutator<String> mutator = HFactory.createMutator(keyspace,stringSerializer);
            mutator.addInsertion(queue.trim(), columnFamily,
                    HFactory.createColumn(messageId, message,stringSerializer, bytesArraySerializer));
            mutator.execute();

        } catch (Exception e) {
            throw new CassandraDataAccessException("Error while adding message to Queue" ,e);
        }
    }

    /**
     * Add a Mapping to a Given Row in cassandra column family.
     * Mappings are used as search indexes
     * @param columnFamily  columnFamilyName
     * @param row  row name
     * @param cKey  key name for the adding column
     * @param cValue  value for the adding column
     * @param keyspace Cassandra KeySpace
     * @throws CassandraDataAccessException  In case of database access error or data error
     */
    public static void addMappingToRaw(String columnFamily, String row, String cKey, String cValue,
                                       Keyspace keyspace) throws CassandraDataAccessException {

        try {
            Mutator<String> mutator = HFactory.createMutator(keyspace, stringSerializer);
            mutator.addInsertion(row, columnFamily,
                    HFactory.createColumn(cKey, cValue.trim(), stringSerializer, stringSerializer));
            mutator.execute();
        } catch (Exception e) {
            throw new CassandraDataAccessException("Error while adding a Mapping to row ", e);
        }
    }

    /**
     *  Add an Mapping Entry to a raw in a given column family
     * @param columnFamily ColumnFamily name
     * @param row  row name
     * @param cKey column key
     * @param cValue column value
     * @param mutator mutator
     * @param execute  should we execute the insertion. if false it will just and the insertion to the mutator
     * @throws CassandraDataAccessException  In case of database access error or data error
     */
    public static void addMappingToRaw(String columnFamily, String row, String cKey, String cValue,
                                       Mutator<String> mutator, boolean execute)
            throws CassandraDataAccessException {

        try {
            mutator.addInsertion(row, columnFamily,
                    HFactory.createColumn(cKey, cValue.trim(), stringSerializer, stringSerializer));

            if (execute) {
                mutator.execute();
            }
        } catch (Exception e) {
            throw new CassandraDataAccessException("Error while adding a Mapping to row ", e);
        }
    }

    /**
     * Delete a given string column in a raw in a column family
     * @param columnFamily  column family name
     * @param row  row name
     * @param key  key name
     * @param keyspace cassandra keySpace
     * @throws CassandraDataAccessException  In case of database access error or data error
     */
    public static void deleteStringColumnFromRaw(String columnFamily,String row, String key ,Keyspace keyspace)
            throws CassandraDataAccessException {
         try {
            Mutator<String> mutator = HFactory.createMutator(keyspace, stringSerializer);
            mutator.addDeletion(row, columnFamily, key, stringSerializer);
            mutator.execute();
        } catch (Exception e) {
           throw new CassandraDataAccessException("Error while deleting " + key + " from " + columnFamily);
        }
    }
}
