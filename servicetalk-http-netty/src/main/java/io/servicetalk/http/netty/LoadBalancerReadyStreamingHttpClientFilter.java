/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.internal.LoadBalancerReadyEvent;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.RetryableException;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;

/**
 * A {@link StreamingHttpClient} filter that will account for transient failures introduced by a {@link LoadBalancer}
 * not being ready for {@link #request(HttpExecutionStrategy, StreamingHttpRequest)} and retry/delay requests until the
 * {@link LoadBalancer} is ready.
 */
final class LoadBalancerReadyStreamingHttpClientFilter extends StreamingHttpClientFilter {
    private final LoadBalancerReadySubscriber loadBalancerReadySubscriber;
    private final int maxRetryCount;

    /**
     * Create a new instance.
     *
     * @param maxRetryCount The maximum number of retries when requests fail with a {@link RetryableException}.
     * @param loadBalancerEvents See {@link LoadBalancer#eventStream()}. This filter will listen for
     * {@link LoadBalancerReadyEvent} events to trigger retries.
     * @param next The next {@link StreamingHttpClient} in the filter chain.
     */
    LoadBalancerReadyStreamingHttpClientFilter(
            int maxRetryCount, Publisher<Object> loadBalancerEvents, FilterableStreamingHttpClient next) {
        super(next);
        if (maxRetryCount <= 0) {
            throw new IllegalArgumentException("maxRetryCount " + maxRetryCount + " (expected >0)");
        }
        this.maxRetryCount = maxRetryCount;
        loadBalancerReadySubscriber = new LoadBalancerReadySubscriber();
        toSource(loadBalancerEvents).subscribe(loadBalancerReadySubscriber);
    }

    @Override
    public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
            final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
        return delegate().reserveConnection(strategy, metaData)
                .retryWhen(retryWhenFunction());
    }

    @Override
    protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                    final HttpExecutionStrategy strategy,
                                                    final StreamingHttpRequest request) {
        return delegate.request(strategy, request).retryWhen(retryWhenFunction());
    }

    private BiIntFunction<Throwable, Completable> retryWhenFunction() {
        return (count, cause) -> count <= maxRetryCount && cause instanceof NoAvailableHostException ?
                loadBalancerReadySubscriber.onHostsAvailable() : failed(cause);
    }
}
