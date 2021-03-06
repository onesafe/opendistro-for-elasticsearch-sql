/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.sql.unittest;

import com.alibaba.druid.sql.parser.ParserException;
import com.amazon.opendistroforelasticsearch.sql.esintgtest.TestsConstants;
import com.amazon.opendistroforelasticsearch.sql.exception.SqlParseException;
import com.amazon.opendistroforelasticsearch.sql.query.ESActionFactory;
import com.amazon.opendistroforelasticsearch.sql.query.QueryAction;
import com.amazon.opendistroforelasticsearch.sql.query.SqlElasticRequestBuilder;
import com.amazon.opendistroforelasticsearch.sql.request.SqlRequest;
import com.amazon.opendistroforelasticsearch.sql.util.CheckScriptContents;
import com.google.common.io.Files;
import org.elasticsearch.client.Client;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLFeatureNotSupportedException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class JSONRequestTest {

    @Test
    public void searchSanity() throws IOException {
        String result = explain(String.format("{\"query\":\"" +
                "SELECT * " +
                "FROM %s " +
                "WHERE firstname LIKE 'A%%' AND age > 20 " +
                "GROUP BY gender " +
                "ORDER BY _score\"}", TestsConstants.TEST_INDEX_ACCOUNT));
        String expectedOutput = Files.toString(
                new File(getResourcePath() + "src/test/resources/expectedOutput/search_explain.json"), StandardCharsets.UTF_8)
                .replaceAll("\r", "");

        assertThat(removeSpaces(result), equalTo(removeSpaces(expectedOutput)));
    }

    @Test
    public void aggregationQuery() throws IOException {
        String result = explain(String.format("{\"query\":\"" +
                "SELECT a, CASE WHEN gender='0' THEN 'aaa' ELSE 'bbb' END AS a2345, count(c) " +
                "FROM %s " +
                "GROUP BY terms('field'='a','execution_hint'='global_ordinals'), a2345\"}", TestsConstants.TEST_INDEX_ACCOUNT));
        String expectedOutput = Files.toString(
                new File(getResourcePath() + "src/test/resources/expectedOutput/aggregation_query_explain.json"), StandardCharsets.UTF_8)
                .replaceAll("\r", "");

        assertThat(removeSpaces(result), equalTo(removeSpaces(expectedOutput)));
    }

    @Test
    public void deleteSanity() throws IOException {
        String result = explain(String.format("{\"query\":\"" +
                "DELETE " +
                "FROM %s " +
                "WHERE firstname LIKE 'A%%' AND age > 20\"}", TestsConstants.TEST_INDEX_ACCOUNT));
        String expectedOutput = Files.toString(
                new File(getResourcePath() + "src/test/resources/expectedOutput/delete_explain.json"), StandardCharsets.UTF_8)
                .replaceAll("\r", "");

        assertThat(removeSpaces(result), equalTo(removeSpaces(expectedOutput)));
    }

    @Test
    public void queryFilter() throws IOException {
        /*
         * Human readable format of the request defined below:
         * {
         *   "query": "SELECT * FROM accounts WHERE age > 25",
         *   "filter": {
         *     "range": {
         *       "balance": {
         *         "lte": 30000
         *       }
         *     }
         *   }
         * }
         */
        String result = explain(String.format("{\"query\":\"" +
                "SELECT * " +
                "FROM %s " +
                "WHERE age > 25\"," +
                "\"filter\":{\"range\":{\"balance\":{\"lte\":30000}}}}", TestsConstants.TEST_INDEX_ACCOUNT));
        String expectedOutput = Files.toString(
                new File(getResourcePath() + "src/test/resources/expectedOutput/json_filter_explain.json"), StandardCharsets.UTF_8)
                .replaceAll("\r", "");

        assertThat(removeSpaces(result), equalTo(removeSpaces(expectedOutput)));
    }

    private String removeSpaces(String s) {
        return s.replaceAll("\\s+", "");
    }

    private String explain(String request) {
        try {
            JSONObject jsonRequest = new JSONObject(request);
            String sql = jsonRequest.getString("query");

            return translate(sql, jsonRequest);
        } catch (SqlParseException | SQLFeatureNotSupportedException e) {
            throw new ParserException("Illegal sql expr in request: " + request);
        }
    }

    private String translate(String sql, JSONObject jsonRequest) throws SQLFeatureNotSupportedException, SqlParseException {
        Client mockClient = Mockito.mock(Client.class);
        CheckScriptContents.stubMockClient(mockClient);
        QueryAction queryAction = ESActionFactory.create(mockClient, sql);

        SqlRequest sqlRequest = new SqlRequest(sql, jsonRequest);
        queryAction.setSqlRequest(sqlRequest);

        SqlElasticRequestBuilder requestBuilder = queryAction.explain();
        return requestBuilder.explain();
    }

    private String getResourcePath() {
        String projectRoot = System.getProperty("project.root");
        if ( projectRoot!= null && projectRoot.trim().length() > 0) {
            return projectRoot.trim() + "/";
        } else {
            return "";
        }
    }
}
