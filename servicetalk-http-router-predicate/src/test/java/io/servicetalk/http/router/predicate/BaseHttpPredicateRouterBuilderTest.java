/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.predicate;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.ReadOnlyBufferAllocators;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.util.Iterator;
import java.util.Spliterator;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public abstract class BaseHttpPredicateRouterBuilderTest {
    static final BufferAllocator allocator = ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
    static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule().silent();
    @Rule
    public final ExpectedException expected = ExpectedException.none();
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Mock
    StreamingHttpService serviceA, serviceB, serviceC, serviceD, serviceE, fallbackService;
    @Mock
    HttpServiceContext ctx;
    @Mock
    HttpExecutionContext executionCtx;
    @Mock
    StreamingHttpRequest request;
    @Mock
    StreamingHttpResponseFactory factory;
    @Mock
    HttpHeaders headers;
    @Mock
    Single<StreamingHttpResponse> responseA, responseB, responseC, responseD, responseE, fallbackResponse;

    @Before
    public void setUp() {
        when(ctx.executionContext()).thenReturn(executionCtx);
        when(executionCtx.executor()).thenReturn(immediate());
        when(executionCtx.executionStrategy()).thenReturn(defaultStrategy());
        when(request.version()).thenReturn(HTTP_1_1);
        when(request.headers()).thenReturn(headers);
        when(factory.newResponse(any(HttpResponseStatus.class))).thenAnswer(
                (Answer<StreamingHttpResponse>) invocation -> {
                    HttpResponseStatus status = invocation.getArgument(0);
                    return reqRespFactory.newResponse(status);
                });

        when(serviceA.handle(eq(ctx), eq(request), any())).thenReturn(responseA);
        when(serviceB.handle(eq(ctx), eq(request), any())).thenReturn(responseB);
        when(serviceC.handle(eq(ctx), eq(request), any())).thenReturn(responseC);
        when(serviceD.handle(eq(ctx), eq(request), any())).thenReturn(responseD);
        when(serviceE.handle(eq(ctx), eq(request), any())).thenReturn(responseE);
        when(fallbackService.handle(eq(ctx), eq(request), any())).thenReturn(fallbackResponse);

        when(serviceA.closeAsync()).thenReturn(completed());
        when(serviceB.closeAsync()).thenReturn(completed());
        when(serviceC.closeAsync()).thenReturn(completed());
        when(serviceD.closeAsync()).thenReturn(completed());
        when(serviceE.closeAsync()).thenReturn(completed());
        when(fallbackService.closeAsync()).thenReturn(completed());

        when(serviceA.closeAsyncGracefully()).thenReturn(completed());
        when(serviceB.closeAsyncGracefully()).thenReturn(completed());
        when(serviceC.closeAsyncGracefully()).thenReturn(completed());
        when(serviceD.closeAsyncGracefully()).thenReturn(completed());
        when(serviceE.closeAsyncGracefully()).thenReturn(completed());
        when(fallbackService.closeAsyncGracefully()).thenReturn(completed());
    }

    @SuppressWarnings("unchecked")
    <T> Answer<Iterator<T>> answerIteratorOf(final T... values) {
        return invocation -> asList(values).iterator();
    }

    @SuppressWarnings("unchecked")
    <T> Answer<Spliterator<T>> answerSpliteratorOf(final T... values) {
        return invocation -> asList(values).spliterator();
    }
}
