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

import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Relational expression that uses Solr calling convention.
 */
public interface SolrRel extends RelNode {
  /**
   * Calling convention for relational operations that occur in Cassandra.
   */
  Convention CONVENTION = new Convention.Impl("SOLR", SolrRel.class);

  void implement(Implementor implementor);

  /**
   * Callback for the implementation process that converts a tree of {@link SolrRel} nodes into a Solr query.
   */
  class Implementor {
    final Map<String, String> fieldMappings = new HashMap<>();
    final List<String> filterQueries = new ArrayList<>();
    final List<String> order = new ArrayList<>();
    String limitValue = null;
    RelOptTable table;
    SolrTable solrTable;

    /**
     * Adds newly projected fields and restricted filterQueries.
     *
     * @param fields        New fields to be projected from a query
     * @param filterQueries New filterQueries to be applied to the query
     */
    public void add(Map<String, String> fieldMappings, List<String> filterQueries) {
      if (fieldMappings != null) {
        this.fieldMappings.putAll(fieldMappings);
      }
      if (filterQueries != null) {
        this.filterQueries.addAll(filterQueries);
      }
    }

    public void addOrder(List<String> newOrder) {
      order.addAll(newOrder);
    }

    public void setLimit(String limit) {
      limitValue = limit;
    }

    public void visitChild(int ordinal, RelNode input) {
      assert ordinal == 0;
      ((SolrRel) input).implement(this);
    }
  }
}
