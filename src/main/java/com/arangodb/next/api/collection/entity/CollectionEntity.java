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

package com.arangodb.next.api.collection.entity;

import com.arangodb.velocypack.annotations.VPackPOJOBuilder;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * @author Michele Rastelli
 * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html">API Documentation</a>
 */
@Value.Immutable
public interface CollectionEntity {

    @VPackPOJOBuilder
    static ImmutableCollectionEntity.Builder builder() {
        return ImmutableCollectionEntity.builder();
    }

    /**
     * @see CollectionCreateOptions#getName()
     */
    String getName();

    /**
     * @see CollectionCreateOptions#getIsSystem()
     */
    Boolean getIsSystem();

    /**
     * @see CollectionCreateOptions#getType()
     */
    CollectionType getType();

    /**
     * @return collection status
     * @apiNote MMFiles storage engine only
     */
    @Nullable
    CollectionStatus getStatus();

    /**
     * @return unique identifier of the collection
     * @deprecated use {@link #getName()}
     */
    String getId();

    /**
     * @return unique identifier of the collection
     */
    String getGloballyUniqueId();

}
