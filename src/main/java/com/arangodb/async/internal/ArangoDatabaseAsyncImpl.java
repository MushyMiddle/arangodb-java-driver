/*
 * DISCLAIMER
 *
 * Copyright 2016 ArangoDB GmbH, Cologne, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright holder is ArangoDB GmbH, Cologne, Germany
 */

package com.arangodb.async.internal;

import com.arangodb.ArangoDBException;
import com.arangodb.DbName;
import com.arangodb.async.ArangoCollectionAsync;
import com.arangodb.async.ArangoCursorAsync;
import com.arangodb.async.ArangoDatabaseAsync;
import com.arangodb.async.ArangoGraphAsync;
import com.arangodb.async.ArangoRouteAsync;
import com.arangodb.async.ArangoSearchAsync;
import com.arangodb.async.ArangoViewAsync;
import com.arangodb.entity.*;
import com.arangodb.entity.arangosearch.AnalyzerEntity;
import com.arangodb.entity.arangosearch.analyzer.SearchAnalyzer;
import com.arangodb.internal.ArangoCursorExecute;
import com.arangodb.internal.ArangoErrors;
import com.arangodb.internal.InternalArangoDatabase;
import com.arangodb.internal.net.HostHandle;
import com.arangodb.internal.util.DocumentUtil;
import com.arangodb.model.AqlFunctionCreateOptions;
import com.arangodb.model.AqlFunctionDeleteOptions;
import com.arangodb.model.AqlFunctionGetOptions;
import com.arangodb.model.AqlQueryExplainOptions;
import com.arangodb.model.AqlQueryOptions;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.CollectionsReadOptions;
import com.arangodb.model.DocumentReadOptions;
import com.arangodb.model.GraphCreateOptions;
import com.arangodb.model.StreamTransactionOptions;
import com.arangodb.model.TransactionOptions;
import com.arangodb.model.TraversalOptions;
import com.arangodb.model.arangosearch.AnalyzerDeleteOptions;
import com.arangodb.model.arangosearch.ArangoSearchCreateOptions;
import com.arangodb.velocypack.Type;
import com.arangodb.velocystream.Request;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */
public class ArangoDatabaseAsyncImpl extends InternalArangoDatabase<ArangoDBAsyncImpl, ArangoExecutorAsync>
        implements ArangoDatabaseAsync {

    ArangoDatabaseAsyncImpl(final ArangoDBAsyncImpl arangoDB, final DbName name) {
        super(arangoDB, name);
    }

    @Override
    public CompletableFuture<ArangoDBVersion> getVersion() {
        return executor.execute(getVersionRequest(), ArangoDBVersion.class);
    }

    @Override
    public CompletableFuture<ArangoDBEngine> getEngine() {
        return executor.execute(getEngineRequest(), ArangoDBEngine.class);
    }

    @Override
    public CompletableFuture<Boolean> exists() {
        return getInfo()
                .handle((result, ex) -> {
                    if (result != null) {
                        return true;
                    }

                    if (ex instanceof CompletionException && ex.getCause() instanceof ArangoDBException) {
                        ArangoDBException e = (ArangoDBException) ex.getCause();
                        if (ArangoErrors.ERROR_ARANGO_DATABASE_NOT_FOUND.equals(e.getErrorNum())) {
                            return false;
                        }
                    }

                    throw new CompletionException(ex);
                });
    }

    @Override
    public CompletableFuture<Collection<String>> getAccessibleDatabases() {
        return executor.execute(getAccessibleDatabasesRequest(), getDatabaseResponseDeserializer());
    }

    @Override
    public ArangoCollectionAsync collection(final String name) {
        return new ArangoCollectionAsyncImpl(this, name);
    }

    @Override
    public CompletableFuture<CollectionEntity> createCollection(final String name) {
        return executor.execute(createCollectionRequest(name, new CollectionCreateOptions()), CollectionEntity.class);
    }

    @Override
    public CompletableFuture<CollectionEntity> createCollection(
            final String name,
            final CollectionCreateOptions options) {
        return executor.execute(createCollectionRequest(name, options), CollectionEntity.class);
    }

    @Override
    public CompletableFuture<Collection<CollectionEntity>> getCollections() {
        return executor.execute(getCollectionsRequest(new CollectionsReadOptions()),
                getCollectionsResponseDeserializer());
    }

    @Override
    public CompletableFuture<Collection<CollectionEntity>> getCollections(final CollectionsReadOptions options) {
        return executor.execute(getCollectionsRequest(options), getCollectionsResponseDeserializer());
    }

    @Override
    public CompletableFuture<IndexEntity> getIndex(final String id) {
        DocumentUtil.validateIndexId(id);
        final String[] split = id.split("/");
        return collection(split[0]).getIndex(split[1]);
    }

    @Override
    public CompletableFuture<String> deleteIndex(final String id) {
        DocumentUtil.validateIndexId(id);
        final String[] split = id.split("/");
        return collection(split[0]).deleteIndex(split[1]);
    }

    @Override
    public CompletableFuture<Boolean> create() {
        return arango().createDatabase(dbName());
    }

    @Override
    public CompletableFuture<Boolean> drop() {
        return executor.execute(dropRequest(), createDropResponseDeserializer());
    }

    @Override
    public CompletableFuture<Void> grantAccess(final String user, final Permissions permissions) {
        return executor.execute(grantAccessRequest(user, permissions), Void.class);
    }

    @Override
    public CompletableFuture<Void> grantAccess(final String user) {
        return executor.execute(grantAccessRequest(user, Permissions.RW), Void.class);
    }

    @Override
    public CompletableFuture<Void> revokeAccess(final String user) {
        return executor.execute(grantAccessRequest(user, Permissions.NONE), Void.class);
    }

    @Override
    public CompletableFuture<Void> resetAccess(final String user) {
        return executor.execute(resetAccessRequest(user), Void.class);
    }

    @Override
    public CompletableFuture<Void> grantDefaultCollectionAccess(final String user, final Permissions permissions) {
        return executor.execute(updateUserDefaultCollectionAccessRequest(user, permissions), Void.class);
    }

    @Override
    public CompletableFuture<Permissions> getPermissions(final String user) {
        return executor.execute(getPermissionsRequest(user), getPermissionsResponseDeserialzer());
    }

    @Override
    public <T> CompletableFuture<ArangoCursorAsync<T>> query(
            final String query,
            final Map<String, Object> bindVars,
            final AqlQueryOptions options,
            final Class<T> type) {
        final Request request = queryRequest(query, bindVars, options);
        final HostHandle hostHandle = new HostHandle();
        final CompletableFuture<CursorEntity> execution = executor.execute(request, CursorEntity.class, hostHandle);
        return execution.thenApply(result -> createCursor(result, type, options, hostHandle));
    }

    @Override
    public <T> CompletableFuture<ArangoCursorAsync<T>> query(
            final String query,
            final AqlQueryOptions options,
            final Class<T> type) {
        return query(query, null, options, type);
    }

    @Override
    public <T> CompletableFuture<ArangoCursorAsync<T>> query(
            final String query,
            final Map<String, Object> bindVars,
            final Class<T> type) {
        return query(query, bindVars, null, type);
    }

    @Override
    public <T> CompletableFuture<ArangoCursorAsync<T>> query(final String query, final Class<T> type) {
        return query(query, null, null, type);
    }

    @Override
    public <T> CompletableFuture<ArangoCursorAsync<T>> cursor(final String cursorId, final Class<T> type) {
        final HostHandle hostHandle = new HostHandle();
        final CompletableFuture<CursorEntity> execution = executor.execute(queryNextRequest(cursorId, null, null), CursorEntity.class, hostHandle);
        return execution.thenApply(result -> createCursor(result, type, null, hostHandle));
    }

    private <T> ArangoCursorAsync<T> createCursor(
            final CursorEntity result,
            final Class<T> type,
            final AqlQueryOptions options,
            final HostHandle hostHandle) {
        return new ArangoCursorAsyncImpl<>(this, new ArangoCursorExecute() {
            @Override
            public CursorEntity next(final String id, Map<String, String> meta) {
                final CompletableFuture<CursorEntity> result = executor.execute(queryNextRequest(id, options, meta),
                        CursorEntity.class, hostHandle);
                try {
                    return result.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new ArangoDBException(e);
                }
            }

            @Override
            public void close(final String id, Map<String, String> meta) {
                try {
                    executor.execute(queryCloseRequest(id, options, meta), Void.class, hostHandle).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new ArangoDBException(e);
                }
            }
        }, type, result);
    }

    @Override
    public CompletableFuture<AqlExecutionExplainEntity> explainQuery(
            final String query,
            final Map<String, Object> bindVars,
            final AqlQueryExplainOptions options) {
        return executor.execute(explainQueryRequest(query, bindVars, options), AqlExecutionExplainEntity.class);
    }

    @Override
    public CompletableFuture<AqlParseEntity> parseQuery(final String query) {
        return executor.execute(parseQueryRequest(query), AqlParseEntity.class);
    }

    @Override
    public CompletableFuture<Void> clearQueryCache() {
        return executor.execute(clearQueryCacheRequest(), Void.class);
    }

    @Override
    public CompletableFuture<QueryCachePropertiesEntity> getQueryCacheProperties() {
        return executor.execute(getQueryCachePropertiesRequest(), QueryCachePropertiesEntity.class);
    }

    @Override
    public CompletableFuture<QueryCachePropertiesEntity> setQueryCacheProperties(
            final QueryCachePropertiesEntity properties) {
        return executor.execute(setQueryCachePropertiesRequest(properties), QueryCachePropertiesEntity.class);
    }

    @Override
    public CompletableFuture<QueryTrackingPropertiesEntity> getQueryTrackingProperties() {
        return executor.execute(getQueryTrackingPropertiesRequest(), QueryTrackingPropertiesEntity.class);
    }

    @Override
    public CompletableFuture<QueryTrackingPropertiesEntity> setQueryTrackingProperties(
            final QueryTrackingPropertiesEntity properties) {
        return executor.execute(setQueryTrackingPropertiesRequest(properties), QueryTrackingPropertiesEntity.class);
    }

    @Override
    public CompletableFuture<Collection<QueryEntity>> getCurrentlyRunningQueries() {
        return executor.execute(getCurrentlyRunningQueriesRequest(), new Type<Collection<QueryEntity>>() {
        }.getType());
    }

    @Override
    public CompletableFuture<Collection<QueryEntity>> getSlowQueries() {
        return executor.execute(getSlowQueriesRequest(), new Type<Collection<QueryEntity>>() {
        }.getType());
    }

    @Override
    public CompletableFuture<Void> clearSlowQueries() {
        return executor.execute(clearSlowQueriesRequest(), Void.class);
    }

    @Override
    public CompletableFuture<Void> killQuery(final String id) {
        return executor.execute(killQueryRequest(id), Void.class);
    }

    @Override
    public CompletableFuture<Void> createAqlFunction(
            final String name,
            final String code,
            final AqlFunctionCreateOptions options) {
        return executor.execute(createAqlFunctionRequest(name, code, options), Void.class);

    }

    @Override
    public CompletableFuture<Integer> deleteAqlFunction(final String name, final AqlFunctionDeleteOptions options) {
        return executor.execute(deleteAqlFunctionRequest(name, options), deleteAqlFunctionResponseDeserializer());
    }

    @Override
    public CompletableFuture<Collection<AqlFunctionEntity>> getAqlFunctions(final AqlFunctionGetOptions options) {
        return executor.execute(getAqlFunctionsRequest(options), getAqlFunctionsResponseDeserializer());
    }

    @Override
    public ArangoGraphAsync graph(final String name) {
        return new ArangoGraphAsyncImpl(this, name);
    }

    @Override
    public CompletableFuture<GraphEntity> createGraph(
            final String name,
            final Collection<EdgeDefinition> edgeDefinitions) {
        return executor.execute(createGraphRequest(name, edgeDefinitions, new GraphCreateOptions()),
                createGraphResponseDeserializer());
    }

    @Override
    public CompletableFuture<GraphEntity> createGraph(
            final String name,
            final Collection<EdgeDefinition> edgeDefinitions,
            final GraphCreateOptions options) {
        return executor.execute(createGraphRequest(name, edgeDefinitions, options), createGraphResponseDeserializer());
    }

    @Override
    public CompletableFuture<Collection<GraphEntity>> getGraphs() {
        return executor.execute(getGraphsRequest(), getGraphsResponseDeserializer());
    }

    @Override
    public <T> CompletableFuture<T> transaction(
            final String action,
            final Class<T> type,
            final TransactionOptions options) {
        return executor.execute(transactionRequest(action, options), transactionResponseDeserializer(type));
    }

    @Override
    public CompletableFuture<StreamTransactionEntity> beginStreamTransaction(StreamTransactionOptions options) {
        return executor.execute(beginStreamTransactionRequest(options), streamTransactionResponseDeserializer());
    }

    @Override
    public CompletableFuture<StreamTransactionEntity> abortStreamTransaction(String id) {
        return executor.execute(abortStreamTransactionRequest(id), streamTransactionResponseDeserializer());
    }

    @Override
    public CompletableFuture<StreamTransactionEntity> getStreamTransaction(String id) {
        return executor.execute(getStreamTransactionRequest(id), streamTransactionResponseDeserializer());
    }

    @Override
    public CompletableFuture<Collection<TransactionEntity>> getStreamTransactions() {
        return executor.execute(getStreamTransactionsRequest(), transactionsResponseDeserializer());
    }

    @Override
    public CompletableFuture<StreamTransactionEntity> commitStreamTransaction(String id) {
        return executor.execute(commitStreamTransactionRequest(id), streamTransactionResponseDeserializer());
    }

    @Override
    public CompletableFuture<DatabaseEntity> getInfo() {
        return executor.execute(getInfoRequest(), getInfoResponseDeserializer());
    }

    @Override
    public <V, E> CompletableFuture<TraversalEntity<V, E>> executeTraversal(
            final Class<V> vertexClass,
            final Class<E> edgeClass,
            final TraversalOptions options) {
        final Request request = executeTraversalRequest(options);
        return executor.execute(request, executeTraversalResponseDeserializer(vertexClass, edgeClass));
    }

    @Override
    public <T> CompletableFuture<T> getDocument(final String id, final Class<T> type) throws ArangoDBException {
        DocumentUtil.validateDocumentId(id);
        final String[] split = id.split("/");
        return collection(split[0]).getDocument(split[1], type);
    }

    @Override
    public <T> CompletableFuture<T> getDocument(final String id, final Class<T> type, final DocumentReadOptions options)
            throws ArangoDBException {
        DocumentUtil.validateDocumentId(id);
        final String[] split = id.split("/");
        return collection(split[0]).getDocument(split[1], type, options);
    }

    @Override
    public CompletableFuture<Void> reloadRouting() {
        return executor.execute(reloadRoutingRequest(), Void.class);
    }

    @Override
    public ArangoRouteAsync route(final String... path) {
        return new ArangoRouteAsyncImpl(this, String.join("/", path), Collections.emptyMap());
    }

    @Override
    public CompletableFuture<Collection<ViewEntity>> getViews() {
        return executor.execute(getViewsRequest(), getViewsResponseDeserializer());
    }

    @Override
    public ArangoViewAsync view(final String name) {
        return new ArangoViewAsyncImpl(this, name);
    }

    @Override
    public ArangoSearchAsync arangoSearch(final String name) {
        return new ArangoSearchAsyncImpl(this, name);
    }

    @Override
    public CompletableFuture<ViewEntity> createView(final String name, final ViewType type) {
        return executor.execute(createViewRequest(name, type), ViewEntity.class);
    }

    @Override
    public CompletableFuture<ViewEntity> createArangoSearch(final String name, final ArangoSearchCreateOptions options) {
        return executor.execute(createArangoSearchRequest(name, options), ViewEntity.class);
    }

    @Override
    public CompletableFuture<AnalyzerEntity> createAnalyzer(AnalyzerEntity options) {
        return executor.execute(createAnalyzerRequest(options), AnalyzerEntity.class);
    }

    @Override
    public CompletableFuture<SearchAnalyzer> createSearchAnalyzer(SearchAnalyzer analyzer) {
        return executor.execute(createAnalyzerRequest(analyzer), SearchAnalyzer.class);
    }

    @Override
    public CompletableFuture<AnalyzerEntity> getAnalyzer(String name) {
        return executor.execute(getAnalyzerRequest(name), AnalyzerEntity.class);
    }

    @Override
    public CompletableFuture<SearchAnalyzer> getSearchAnalyzer(String name) {
        return executor.execute(getAnalyzerRequest(name), SearchAnalyzer.class);
    }

    @Override
    public CompletableFuture<Collection<AnalyzerEntity>> getAnalyzers() {
        return executor.execute(getAnalyzersRequest(), getAnalyzersResponseDeserializer());
    }

    @Override
    public CompletableFuture<Collection<SearchAnalyzer>> getSearchAnalyzers() {
        return executor.execute(getAnalyzersRequest(), getSearchAnalyzersResponseDeserializer());
    }

    @Override
    public CompletableFuture<Void> deleteAnalyzer(String name) {
        return executor.execute(deleteAnalyzerRequest(name, null), Void.class);
    }

    @Override
    public CompletableFuture<Void> deleteAnalyzer(String name, AnalyzerDeleteOptions options) {
        return executor.execute(deleteAnalyzerRequest(name, options), Void.class);
    }

    @Override
    public CompletableFuture<Void> deleteSearchAnalyzer(String name) {
        return deleteAnalyzer(name);
    }

    @Override
    public CompletableFuture<Void> deleteSearchAnalyzer(String name, AnalyzerDeleteOptions options) {
        return deleteAnalyzer(name, options);
    }


}
