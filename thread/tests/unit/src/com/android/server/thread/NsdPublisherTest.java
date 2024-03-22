/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.thread;

import static android.net.nsd.NsdManager.FAILURE_INTERNAL_ERROR;
import static android.net.nsd.NsdManager.PROTOCOL_DNS_SD;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.net.nsd.DiscoveryRequest;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.test.TestLooper;

import com.android.server.thread.openthread.DnsTxtAttribute;
import com.android.server.thread.openthread.INsdDiscoverServiceCallback;
import com.android.server.thread.openthread.INsdResolveServiceCallback;
import com.android.server.thread.openthread.INsdStatusReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/** Unit tests for {@link NsdPublisher}. */
public final class NsdPublisherTest {
    @Mock private NsdManager mMockNsdManager;

    @Mock private INsdStatusReceiver mRegistrationReceiver;
    @Mock private INsdStatusReceiver mUnregistrationReceiver;
    @Mock private INsdDiscoverServiceCallback mDiscoverServiceCallback;
    @Mock private INsdResolveServiceCallback mResolveServiceCallback;

    private TestLooper mTestLooper;
    private NsdPublisher mNsdPublisher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void registerService_nsdManagerSucceeds_serviceRegistrationSucceeds() throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mTestLooper.dispatchAll();

        assertThat(actualServiceInfo.getServiceName()).isEqualTo("MyService");
        assertThat(actualServiceInfo.getServiceType()).isEqualTo("_test._tcp");
        assertThat(actualServiceInfo.getSubtypes()).isEqualTo(Set.of("_subtype1", "_subtype2"));
        assertThat(actualServiceInfo.getPort()).isEqualTo(12345);
        assertThat(actualServiceInfo.getAttributes().size()).isEqualTo(2);
        assertThat(actualServiceInfo.getAttributes().get("key1"))
                .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x02});
        assertThat(actualServiceInfo.getAttributes().get("key2"))
                .isEqualTo(new byte[] {(byte) 0x03});

        verify(mRegistrationReceiver, times(1)).onSuccess();
    }

    @Test
    public void registerService_nsdManagerFails_serviceRegistrationFails() throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onRegistrationFailed(actualServiceInfo, FAILURE_INTERNAL_ERROR);
        mTestLooper.dispatchAll();

        assertThat(actualServiceInfo.getServiceName()).isEqualTo("MyService");
        assertThat(actualServiceInfo.getServiceType()).isEqualTo("_test._tcp");
        assertThat(actualServiceInfo.getSubtypes()).isEqualTo(Set.of("_subtype1", "_subtype2"));
        assertThat(actualServiceInfo.getPort()).isEqualTo(12345);
        assertThat(actualServiceInfo.getAttributes().size()).isEqualTo(2);
        assertThat(actualServiceInfo.getAttributes().get("key1"))
                .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x02});
        assertThat(actualServiceInfo.getAttributes().get("key2"))
                .isEqualTo(new byte[] {(byte) 0x03});

        verify(mRegistrationReceiver, times(1)).onError(FAILURE_INTERNAL_ERROR);
    }

    @Test
    public void registerService_nsdManagerThrows_serviceRegistrationFails() throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        doThrow(new IllegalArgumentException("NsdManager fails"))
                .when(mMockNsdManager)
                .registerService(any(), anyInt(), any(Executor.class), any());

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);
        mTestLooper.dispatchAll();

        verify(mRegistrationReceiver, times(1)).onError(FAILURE_INTERNAL_ERROR);
    }

    @Test
    public void unregisterService_nsdManagerSucceeds_serviceUnregistrationSucceeds()
            throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mNsdPublisher.unregister(mUnregistrationReceiver, 16 /* listenerId */);
        mTestLooper.dispatchAll();
        verify(mMockNsdManager, times(1)).unregisterService(actualRegistrationListener);

        actualRegistrationListener.onServiceUnregistered(actualServiceInfo);
        mTestLooper.dispatchAll();
        verify(mUnregistrationReceiver, times(1)).onSuccess();
    }

    @Test
    public void unregisterService_nsdManagerFails_serviceUnregistrationFails() throws Exception {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mNsdPublisher.unregister(mUnregistrationReceiver, 16 /* listenerId */);
        mTestLooper.dispatchAll();
        verify(mMockNsdManager, times(1)).unregisterService(actualRegistrationListener);

        actualRegistrationListener.onUnregistrationFailed(
                actualServiceInfo, FAILURE_INTERNAL_ERROR);
        mTestLooper.dispatchAll();
        verify(mUnregistrationReceiver, times(1)).onError(0);
    }

    @Test
    public void registerHost_nsdManagerSucceeds_serviceRegistrationSucceeds() throws Exception {
        prepareTest();

        mNsdPublisher.registerHost(
                "MyHost",
                List.of("2001:db8::1", "2001:db8::2", "2001:db8::3"),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mTestLooper.dispatchAll();

        assertThat(actualServiceInfo.getServiceName()).isNull();
        assertThat(actualServiceInfo.getServiceType()).isNull();
        assertThat(actualServiceInfo.getSubtypes()).isEmpty();
        assertThat(actualServiceInfo.getPort()).isEqualTo(0);
        assertThat(actualServiceInfo.getAttributes()).isEmpty();
        assertThat(actualServiceInfo.getHostname()).isEqualTo("MyHost");
        assertThat(actualServiceInfo.getHostAddresses())
                .isEqualTo(makeAddresses("2001:db8::1", "2001:db8::2", "2001:db8::3"));

        verify(mRegistrationReceiver, times(1)).onSuccess();
    }

    @Test
    public void registerHost_nsdManagerFails_serviceRegistrationFails() throws Exception {
        prepareTest();

        mNsdPublisher.registerHost(
                "MyHost",
                List.of("2001:db8::1", "2001:db8::2", "2001:db8::3"),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(),
                        actualRegistrationListenerCaptor.capture());
        mTestLooper.dispatchAll();

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onRegistrationFailed(actualServiceInfo, FAILURE_INTERNAL_ERROR);
        mTestLooper.dispatchAll();

        assertThat(actualServiceInfo.getServiceName()).isNull();
        assertThat(actualServiceInfo.getServiceType()).isNull();
        assertThat(actualServiceInfo.getSubtypes()).isEmpty();
        assertThat(actualServiceInfo.getPort()).isEqualTo(0);
        assertThat(actualServiceInfo.getAttributes()).isEmpty();
        assertThat(actualServiceInfo.getHostname()).isEqualTo("MyHost");
        assertThat(actualServiceInfo.getHostAddresses())
                .isEqualTo(makeAddresses("2001:db8::1", "2001:db8::2", "2001:db8::3"));

        verify(mRegistrationReceiver, times(1)).onError(FAILURE_INTERNAL_ERROR);
    }

    @Test
    public void registerHost_nsdManagerThrows_serviceRegistrationFails() throws Exception {
        prepareTest();

        doThrow(new IllegalArgumentException("NsdManager fails"))
                .when(mMockNsdManager)
                .registerService(any(), anyInt(), any(Executor.class), any());

        mNsdPublisher.registerHost(
                "MyHost",
                List.of("2001:db8::1", "2001:db8::2", "2001:db8::3"),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        verify(mRegistrationReceiver, times(1)).onError(FAILURE_INTERNAL_ERROR);
    }

    @Test
    public void unregisterHost_nsdManagerSucceeds_serviceUnregistrationSucceeds() throws Exception {
        prepareTest();

        mNsdPublisher.registerHost(
                "MyHost",
                List.of("2001:db8::1", "2001:db8::2", "2001:db8::3"),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mNsdPublisher.unregister(mUnregistrationReceiver, 16 /* listenerId */);
        mTestLooper.dispatchAll();
        verify(mMockNsdManager, times(1)).unregisterService(actualRegistrationListener);

        actualRegistrationListener.onServiceUnregistered(actualServiceInfo);
        mTestLooper.dispatchAll();
        verify(mUnregistrationReceiver, times(1)).onSuccess();
    }

    @Test
    public void unregisterHost_nsdManagerFails_serviceUnregistrationFails() throws Exception {
        prepareTest();

        mNsdPublisher.registerHost(
                "MyHost",
                List.of("2001:db8::1", "2001:db8::2", "2001:db8::3"),
                mRegistrationReceiver,
                16 /* listenerId */);

        mTestLooper.dispatchAll();

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());

        NsdServiceInfo actualServiceInfo = actualServiceInfoCaptor.getValue();
        NsdManager.RegistrationListener actualRegistrationListener =
                actualRegistrationListenerCaptor.getValue();

        actualRegistrationListener.onServiceRegistered(actualServiceInfo);
        mNsdPublisher.unregister(mUnregistrationReceiver, 16 /* listenerId */);
        mTestLooper.dispatchAll();
        verify(mMockNsdManager, times(1)).unregisterService(actualRegistrationListener);

        actualRegistrationListener.onUnregistrationFailed(
                actualServiceInfo, FAILURE_INTERNAL_ERROR);
        mTestLooper.dispatchAll();
        verify(mUnregistrationReceiver, times(1)).onError(0);
    }

    @Test
    public void discoverService_serviceDiscovered() throws Exception {
        prepareTest();

        mNsdPublisher.discoverService("_test._tcp", mDiscoverServiceCallback, 10 /* listenerId */);
        mTestLooper.dispatchAll();
        ArgumentCaptor<NsdManager.DiscoveryListener> discoveryListenerArgumentCaptor =
                ArgumentCaptor.forClass(NsdManager.DiscoveryListener.class);
        verify(mMockNsdManager, times(1))
                .discoverServices(
                        eq(new DiscoveryRequest.Builder(PROTOCOL_DNS_SD, "_test._tcp").build()),
                        any(Executor.class),
                        discoveryListenerArgumentCaptor.capture());
        NsdManager.DiscoveryListener actualDiscoveryListener =
                discoveryListenerArgumentCaptor.getValue();
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("test");
        serviceInfo.setServiceType(null);
        actualDiscoveryListener.onServiceFound(serviceInfo);
        mTestLooper.dispatchAll();

        verify(mDiscoverServiceCallback, times(1))
                .onServiceDiscovered("test", "_test._tcp", true /* isFound */);
    }

    @Test
    public void discoverService_serviceLost() throws Exception {
        prepareTest();

        mNsdPublisher.discoverService("_test._tcp", mDiscoverServiceCallback, 10 /* listenerId */);
        mTestLooper.dispatchAll();
        ArgumentCaptor<NsdManager.DiscoveryListener> discoveryListenerArgumentCaptor =
                ArgumentCaptor.forClass(NsdManager.DiscoveryListener.class);
        verify(mMockNsdManager, times(1))
                .discoverServices(
                        eq(new DiscoveryRequest.Builder(PROTOCOL_DNS_SD, "_test._tcp").build()),
                        any(Executor.class),
                        discoveryListenerArgumentCaptor.capture());
        NsdManager.DiscoveryListener actualDiscoveryListener =
                discoveryListenerArgumentCaptor.getValue();
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("test");
        serviceInfo.setServiceType(null);
        actualDiscoveryListener.onServiceLost(serviceInfo);
        mTestLooper.dispatchAll();

        verify(mDiscoverServiceCallback, times(1))
                .onServiceDiscovered("test", "_test._tcp", false /* isFound */);
    }

    @Test
    public void stopServiceDiscovery() {
        prepareTest();

        mNsdPublisher.discoverService("_test._tcp", mDiscoverServiceCallback, 10 /* listenerId */);
        mTestLooper.dispatchAll();
        ArgumentCaptor<NsdManager.DiscoveryListener> discoveryListenerArgumentCaptor =
                ArgumentCaptor.forClass(NsdManager.DiscoveryListener.class);
        verify(mMockNsdManager, times(1))
                .discoverServices(
                        eq(new DiscoveryRequest.Builder(PROTOCOL_DNS_SD, "_test._tcp").build()),
                        any(Executor.class),
                        discoveryListenerArgumentCaptor.capture());
        NsdManager.DiscoveryListener actualDiscoveryListener =
                discoveryListenerArgumentCaptor.getValue();
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("test");
        serviceInfo.setServiceType(null);
        actualDiscoveryListener.onServiceFound(serviceInfo);
        mNsdPublisher.stopServiceDiscovery(10 /* listenerId */);
        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(1)).stopServiceDiscovery(actualDiscoveryListener);
    }

    @Test
    public void resolveService_serviceResolved() throws Exception {
        prepareTest();

        mNsdPublisher.resolveService(
                "test", "_test._tcp", mResolveServiceCallback, 10 /* listenerId */);
        mTestLooper.dispatchAll();
        ArgumentCaptor<NsdServiceInfo> serviceInfoArgumentCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.ServiceInfoCallback> serviceInfoCallbackArgumentCaptor =
                ArgumentCaptor.forClass(NsdManager.ServiceInfoCallback.class);
        verify(mMockNsdManager, times(1))
                .registerServiceInfoCallback(
                        serviceInfoArgumentCaptor.capture(),
                        any(Executor.class),
                        serviceInfoCallbackArgumentCaptor.capture());
        assertThat(serviceInfoArgumentCaptor.getValue().getServiceName()).isEqualTo("test");
        assertThat(serviceInfoArgumentCaptor.getValue().getServiceType()).isEqualTo("_test._tcp");
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("test");
        serviceInfo.setServiceType("_test._tcp");
        serviceInfo.setPort(12345);
        serviceInfo.setHostname("test-host");
        serviceInfo.setHostAddresses(
                List.of(
                        InetAddress.parseNumericAddress("2001::1"),
                        InetAddress.parseNumericAddress("2001::2")));
        serviceInfo.setAttribute("key1", new byte[] {(byte) 0x01, (byte) 0x02});
        serviceInfo.setAttribute("key2", new byte[] {(byte) 0x03});
        serviceInfoCallbackArgumentCaptor.getValue().onServiceUpdated(serviceInfo);
        mTestLooper.dispatchAll();

        verify(mResolveServiceCallback, times(1))
                .onServiceResolved(
                        eq("test-host"),
                        eq("test"),
                        eq("_test._tcp"),
                        eq(12345),
                        eq(List.of("2001::1", "2001::2")),
                        argThat(
                                new TxtMatcher(
                                        List.of(
                                                makeTxtAttribute("key1", List.of(0x01, 0x02)),
                                                makeTxtAttribute("key2", List.of(0x03))))),
                        anyInt());
    }

    @Test
    public void stopServiceResolution() throws Exception {
        prepareTest();

        mNsdPublisher.resolveService(
                "test", "_test._tcp", mResolveServiceCallback, 10 /* listenerId */);
        mTestLooper.dispatchAll();
        ArgumentCaptor<NsdServiceInfo> serviceInfoArgumentCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.ServiceInfoCallback> serviceInfoCallbackArgumentCaptor =
                ArgumentCaptor.forClass(NsdManager.ServiceInfoCallback.class);
        verify(mMockNsdManager, times(1))
                .registerServiceInfoCallback(
                        serviceInfoArgumentCaptor.capture(),
                        any(Executor.class),
                        serviceInfoCallbackArgumentCaptor.capture());
        assertThat(serviceInfoArgumentCaptor.getValue().getServiceName()).isEqualTo("test");
        assertThat(serviceInfoArgumentCaptor.getValue().getServiceType()).isEqualTo("_test._tcp");
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("test");
        serviceInfo.setServiceType("_test._tcp");
        serviceInfo.setPort(12345);
        serviceInfo.setHostname("test-host");
        serviceInfo.setHostAddresses(
                List.of(
                        InetAddress.parseNumericAddress("2001::1"),
                        InetAddress.parseNumericAddress("2001::2")));
        serviceInfo.setAttribute("key1", new byte[] {(byte) 0x01, (byte) 0x02});
        serviceInfo.setAttribute("key2", new byte[] {(byte) 0x03});
        serviceInfoCallbackArgumentCaptor.getValue().onServiceUpdated(serviceInfo);
        mNsdPublisher.stopServiceResolution(10 /* listenerId */);
        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(1))
                .unregisterServiceInfoCallback(serviceInfoCallbackArgumentCaptor.getValue());
    }

    @Test
    public void reset_unregisterAll() {
        prepareTest();

        DnsTxtAttribute txt1 = makeTxtAttribute("key1", List.of(0x01, 0x02));
        DnsTxtAttribute txt2 = makeTxtAttribute("key2", List.of(0x03));

        ArgumentCaptor<NsdServiceInfo> actualServiceInfoCaptor =
                ArgumentCaptor.forClass(NsdServiceInfo.class);
        ArgumentCaptor<NsdManager.RegistrationListener> actualRegistrationListenerCaptor =
                ArgumentCaptor.forClass(NsdManager.RegistrationListener.class);

        mNsdPublisher.registerService(
                null,
                "MyService",
                "_test._tcp",
                List.of("_subtype1", "_subtype2"),
                12345,
                List.of(txt1, txt2),
                mRegistrationReceiver,
                16 /* listenerId */);
        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(1))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());
        NsdManager.RegistrationListener actualListener1 =
                actualRegistrationListenerCaptor.getValue();
        actualListener1.onServiceRegistered(actualServiceInfoCaptor.getValue());

        mNsdPublisher.registerService(
                null,
                "MyService2",
                "_test._udp",
                Collections.emptyList(),
                11111,
                Collections.emptyList(),
                mRegistrationReceiver,
                17 /* listenerId */);

        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(2))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());
        NsdManager.RegistrationListener actualListener2 =
                actualRegistrationListenerCaptor.getAllValues().get(1);
        actualListener2.onServiceRegistered(actualServiceInfoCaptor.getValue());

        mNsdPublisher.registerHost(
                "Myhost",
                List.of("2001:db8::1", "2001:db8::2", "2001:db8::3"),
                mRegistrationReceiver,
                18 /* listenerId */);

        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(3))
                .registerService(
                        actualServiceInfoCaptor.capture(),
                        eq(PROTOCOL_DNS_SD),
                        any(Executor.class),
                        actualRegistrationListenerCaptor.capture());
        NsdManager.RegistrationListener actualListener3 =
                actualRegistrationListenerCaptor.getAllValues().get(1);
        actualListener3.onServiceRegistered(actualServiceInfoCaptor.getValue());

        mNsdPublisher.reset();
        mTestLooper.dispatchAll();

        verify(mMockNsdManager, times(1)).unregisterService(actualListener1);
        verify(mMockNsdManager, times(1)).unregisterService(actualListener2);
        verify(mMockNsdManager, times(1)).unregisterService(actualListener3);
    }

    @Test
    public void onOtDaemonDied_resetIsCalled() {
        prepareTest();
        NsdPublisher spyNsdPublisher = spy(mNsdPublisher);

        spyNsdPublisher.onOtDaemonDied();
        mTestLooper.dispatchAll();

        verify(spyNsdPublisher, times(1)).reset();
    }

    private static DnsTxtAttribute makeTxtAttribute(String name, List<Integer> value) {
        DnsTxtAttribute txtAttribute = new DnsTxtAttribute();

        txtAttribute.name = name;
        txtAttribute.value = new byte[value.size()];

        for (int i = 0; i < value.size(); ++i) {
            txtAttribute.value[i] = value.get(i).byteValue();
        }

        return txtAttribute;
    }

    private static List<InetAddress> makeAddresses(String... addressStrings) {
        List<InetAddress> addresses = new ArrayList<>();

        for (String addressString : addressStrings) {
            addresses.add(InetAddresses.parseNumericAddress(addressString));
        }
        return addresses;
    }

    private static class TxtMatcher implements ArgumentMatcher<List<DnsTxtAttribute>> {
        private final List<DnsTxtAttribute> mAttributes;

        TxtMatcher(List<DnsTxtAttribute> attributes) {
            mAttributes = attributes;
        }

        @Override
        public boolean matches(List<DnsTxtAttribute> argument) {
            if (argument.size() != mAttributes.size()) {
                return false;
            }
            for (int i = 0; i < argument.size(); ++i) {
                if (!Objects.equals(argument.get(i).name, mAttributes.get(i).name)) {
                    return false;
                }
                if (!Arrays.equals(argument.get(i).value, mAttributes.get(i).value)) {
                    return false;
                }
            }
            return true;
        }
    }

    // @Before and @Test run in different threads. NsdPublisher requires the jobs are run on the
    // thread looper, so TestLooper needs to be created inside each test case to install the
    // correct looper.
    private void prepareTest() {
        mTestLooper = new TestLooper();
        Handler handler = new Handler(mTestLooper.getLooper());
        mNsdPublisher = new NsdPublisher(mMockNsdManager, handler);
    }
}
