/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.adapter;

import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.*;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.io.stream.CloudSolrStream;
import org.apache.solr.client.solrj.io.stream.TupleStream;
import org.apache.solr.common.params.CommonParams;

import java.io.IOException;
import java.util.*;

/**
 * Table based on a Solr collection
 */
public class SolrTable extends AbstractQueryableTable implements TranslatableTable {
  private final String collection;
  private final SolrSchema schema;
  private RelProtoDataType protoRowType;

  public SolrTable(SolrSchema schema, String collection) {
    super(Object[].class);
    this.schema = schema;
    this.collection = collection;
  }

  public String toString() {
    return "SolrTable {" + collection + "}";
  }

  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    if (protoRowType == null) {
      protoRowType = schema.getRelDataType(collection);
    }
    return protoRowType.apply(typeFactory);
  }
  
  public Enumerable<Object> query(final CloudSolrClient cloudSolrClient) {
    return query(cloudSolrClient, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null);
  }

  /** Executes a Solr query on the underlying table.
   *
   * @param cloudSolrClient Solr CloudSolrClient
   * @param fields List of fields to project
   * @param filterQueries A list of filterQueries which should be used in the query
   * @return Enumerator of results
   */
  public Enumerable<Object> query(final CloudSolrClient cloudSolrClient, List<String> fields,
                                  List<String> filterQueries, List<String> order, String limit) {
    Map<String, String> solrParams = new HashMap<>();
    solrParams.put(CommonParams.Q, "*:*");
    //solrParams.put(CommonParams.QT, "/export");

    if (fields.isEmpty()) {
      solrParams.put(CommonParams.FL, "*");
    } else {
      solrParams.put(CommonParams.FL, String.join(",", fields));
    }

    if (filterQueries.isEmpty()) {
      solrParams.put(CommonParams.FQ, "*:*");
    } else {
      // SolrParams should be a ModifiableParams instead of a map so we could add multiple FQs
      solrParams.put(CommonParams.FQ, String.join(" OR ", filterQueries));
    }

    // Build and issue the query and return an Enumerator over the results
    if (order.isEmpty()) {
      String DEFAULT_SORT_FIELD = "_version_";
      solrParams.put(CommonParams.SORT, DEFAULT_SORT_FIELD + " desc");

      // Make sure the default sort field is in the field list
      String fl = solrParams.get(CommonParams.FL);
      if(!fl.contains(DEFAULT_SORT_FIELD)) {
        solrParams.put(CommonParams.FL, String.join(",", fl, DEFAULT_SORT_FIELD));
      }
    } else {
      solrParams.put(CommonParams.SORT, String.join(",", order));
    }

//    if (limit != null) {
//      queryBuilder.append(" LIMIT ").append(limit);
//    }

    return new AbstractEnumerable<Object>() {
      public Enumerator<Object> enumerator() {
        TupleStream cloudSolrStream;
        try {
          cloudSolrStream = new CloudSolrStream(cloudSolrClient.getZkHost(), collection, solrParams);
          cloudSolrStream.open();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

        return new SolrEnumerator(cloudSolrStream, fields);
      }
    };
  }

  public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schema, String tableName) {
    return new SolrQueryable<>(queryProvider, schema, this, tableName);
  }

  public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
    final RelOptCluster cluster = context.getCluster();
    return new SolrTableScan(cluster, cluster.traitSetOf(SolrRel.CONVENTION), relOptTable, this, null);
  }

  public static class SolrQueryable<T> extends AbstractTableQueryable<T> {
    SolrQueryable(QueryProvider queryProvider, SchemaPlus schema, SolrTable table, String tableName) {
      super(queryProvider, schema, table, tableName);
    }

    public Enumerator<T> enumerator() {
      //noinspection unchecked
      final Enumerable<T> enumerable = (Enumerable<T>) getTable().query(getCloudSolrClient());
      return enumerable.enumerator();
    }

    private SolrTable getTable() {
      return (SolrTable) table;
    }

    private CloudSolrClient getCloudSolrClient() {
      return schema.unwrap(SolrSchema.class).cloudSolrClient;
    }

    /** Called via code-generation.
     *
     * @see SolrMethod#SOLR_QUERYABLE_QUERY
     */
    @SuppressWarnings("UnusedDeclaration")
    public Enumerable<Object> query(List<String> fields, List<String> filterQueries, List<String> order, String limit) {
      return getTable().query(getCloudSolrClient(), fields, filterQueries, order, limit);
    }
  }
}
