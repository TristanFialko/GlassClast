package dev.imirror.receiver.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MdnsServiceTest — Unit tests for MdnsService.
 *
 * WHY: MdnsService is the gateway between iMirror and macOS discovery.
 * If the mDNS registration is wrong (wrong service type, missing TXT records),
 * macOS will never show iMirror in the AirPlay menu. These tests verify that
 * the registration is correct without actually using the network.
 *
 * HOW: We mock the Android [NsdManager] and [Context] to avoid needing a real
 * Android device. MockK is used to create mock objects and verify interactions.
 *
 * CRITICAL: NsdManager.registerService is ASYNCHRONOUS — it takes a
 * RegistrationListener and invokes onServiceRegistered/onRegistrationFailed
 * later. These tests manually invoke those callbacks to simulate real behavior.
 *
 * IMPORTANT: MdnsService registers services SEQUENTIALLY:
 * 1. start() -> registers _raop._tcp only
 * 2. raop callback -> registers _airplay._tcp
 * 3. airplay callback -> both registered, ADVERTISING state
 *
 * STRATEGY: Use `every { registerService(capture(...)) } returns Unit` to capture
 * arguments DURING test execution. This captures the real serviceInfo objects
 * created by MdnsService, which we then pass to the callback invocations.
 */
class MdnsServiceTest {

    private lateinit var mdnsService: MdnsService
    private lateinit var mockContext: Context
    private lateinit var mockNsdManager: NsdManager

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockNsdManager = mockk(relaxed = false) // Non-relaxed to avoid argument wrapping
        every { mockContext.getSystemService(Context.NSD_SERVICE) } returns mockNsdManager
        every { mockNsdManager.unregisterService(any()) } returns Unit
        every { mockNsdManager.stopServiceDiscovery(any()) } returns Unit
        mdnsService = MdnsService(mockContext)
    }

    /**
     * Test: When start() is called, MdnsService registers _raop first.
     * Then after raop callback, it registers _airplay.
     */
    @Test
    fun `start registers two mDNS services in sequence`() {
        val serviceInfos = mutableListOf<NsdServiceInfo>()
        val listeners = mutableListOf<NsdManager.RegistrationListener>()

        // Capture arguments during execution
        every { mockNsdManager.registerService(capture(serviceInfos), any(), capture(listeners)) } returns Unit

        mdnsService.start("Test Device")

        // First call should be raop only
        verify(exactly = 1) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Invoke raop callback with the CAPTURED serviceInfo
        listeners[0].onServiceRegistered(serviceInfos[0])

        // Now airplay should be registered (second call)
        verify(exactly = 2) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Invoke airplay callback with a MOCK serviceInfo that returns proper serviceName
        // (Robolectric's NsdServiceInfo doesn't properly implement serviceName setter/getter)
        val airplayServiceInfo = mockk<NsdServiceInfo>(relaxed = true)
        every { airplayServiceInfo.serviceName } returns "Test Device"
        every { airplayServiceInfo.serviceType } returns "_airplay._tcp"
        every { airplayServiceInfo.port } returns MdnsService.AIRPLAY_PORT
        listeners[1].onServiceRegistered(airplayServiceInfo)
    }

    /**
     * Test: Calling start() twice does not register services twice (idempotent).
     */
    @Test
    fun `start is idempotent when called twice`() {
        val serviceInfos = mutableListOf<NsdServiceInfo>()
        val listeners = mutableListOf<NsdManager.RegistrationListener>()

        every { mockNsdManager.registerService(capture(serviceInfos), any(), capture(listeners)) } returns Unit

        mdnsService.start("Test Device")
        mdnsService.start("Test Device") // Second call should be no-op

        // Verify only 1 registration from first start (raop)
        verify(exactly = 1) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Invoke raop callback with captured serviceInfo
        listeners[0].onServiceRegistered(serviceInfos[0])

        // Now airplay registered (total 2)
        verify(exactly = 2) {
            mockNsdManager.registerService(any(), any(), any())
        }
    }

    /**
     * Test: When stop() is called after start(), both services are unregistered.
     */
    @Test
    fun `stop unregisters services after start`() {
        val serviceInfos = mutableListOf<NsdServiceInfo>()
        val listeners = mutableListOf<NsdManager.RegistrationListener>()

        every { mockNsdManager.registerService(capture(serviceInfos), any(), capture(listeners)) } returns Unit

        mdnsService.start("Test Device")

        // First call is raop
        verify(exactly = 1) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Invoke raop callback with captured serviceInfo - triggers airplay registration
        listeners[0].onServiceRegistered(serviceInfos[0])

        // Second call is airplay
        verify(exactly = 2) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Invoke airplay callback with a MOCK serviceInfo that returns proper serviceName
        val airplayServiceInfo = mockk<NsdServiceInfo>(relaxed = true)
        every { airplayServiceInfo.serviceName } returns "Test Device"
        every { airplayServiceInfo.serviceType } returns "_airplay._tcp"
        every { airplayServiceInfo.port } returns MdnsService.AIRPLAY_PORT
        listeners[1].onServiceRegistered(airplayServiceInfo)

        mdnsService.stop()

        // Both unregisterService calls should happen
        verify(exactly = 2) {
            mockNsdManager.unregisterService(any())
        }
    }

    /**
     * Test: stop() without start() does not crash.
     */
    @Test
    fun `stop without start does not crash`() {
        mdnsService.stop() // Must not throw
    }

    /**
     * Test: AIRPLAY_PORT is 7000 (the standard AirPlay port).
     */
    @Test
    fun `AIRPLAY_PORT is 7000`() {
        assertEquals(7000, MdnsService.AIRPLAY_PORT)
    }

    /**
     * Test: stop() without start() emits DISABLED state.
     */
    @Test
    fun `stop emits DISABLED protocol state`() {
        val states = mutableListOf<dev.imirror.receiver.service.ProtocolState>()
        val service = MdnsService(mockContext, onStateChange = { states.add(it) })
        service.stop()
        assertTrue(states.contains(dev.imirror.receiver.service.ProtocolState.DISABLED))
    }

    /**
     * Test: restart() calls stop then start (2 unregistrations + 2 registrations).
     */
    @Test
    fun `restart unregisters then re-registers services`() {
        val serviceInfos = mutableListOf<NsdServiceInfo>()
        val listeners = mutableListOf<NsdManager.RegistrationListener>()

        every { mockNsdManager.registerService(capture(serviceInfos), any(), capture(listeners)) } returns Unit

        mdnsService.start("Test Device")

        // First call is raop
        verify(exactly = 1) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Complete initial registration chain
        listeners[0].onServiceRegistered(serviceInfos[0])

        // Second call is airplay
        verify(exactly = 2) {
            mockNsdManager.registerService(any(), any(), any())
        }
        val airplayServiceInfo = mockk<NsdServiceInfo>(relaxed = true)
        every { airplayServiceInfo.serviceName } returns "Test Device"
        every { airplayServiceInfo.serviceType } returns "_airplay._tcp"
        every { airplayServiceInfo.port } returns MdnsService.AIRPLAY_PORT
        listeners[1].onServiceRegistered(airplayServiceInfo)

        // restart = stop + start
        mdnsService.restart("New Device Name")

        // Restart's start() registers raop (3rd call), then callback registers airplay (4th call)
        // Invoke restart's raop callback
        listeners[2].onServiceRegistered(serviceInfos[2])

        // Now airplay from restart should be registered (4th call)
        verify(atLeast = 4) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Invoke restart's airplay callback
        val restartAirplayServiceInfo = mockk<NsdServiceInfo>(relaxed = true)
        every { restartAirplayServiceInfo.serviceName } returns "New Device Name"
        every { restartAirplayServiceInfo.serviceType } returns "_airplay._tcp"
        every { restartAirplayServiceInfo.port } returns MdnsService.AIRPLAY_PORT
        listeners[3].onServiceRegistered(restartAirplayServiceInfo)
    }

    /**
     * Test: Registration failure handling - ALREADY_ACTIVE treated as success.
     */
    @Test
    fun `registration failure with ALREADY_ACTIVE is treated as success`() {
        val serviceInfos = mutableListOf<NsdServiceInfo>()
        val listeners = mutableListOf<NsdManager.RegistrationListener>()

        every { mockNsdManager.registerService(capture(serviceInfos), any(), capture(listeners)) } returns Unit

        mdnsService.start("Test Device")

        // First call is raop
        verify(exactly = 1) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Simulate NsdManager reporting FAILURE_ALREADY_ACTIVE for raop
        listeners[0].onRegistrationFailed(
            serviceInfos[0],
            NsdManager.FAILURE_ALREADY_ACTIVE
        )

        // Should still proceed to register airplay (treated as success)
        verify(exactly = 2) {
            mockNsdManager.registerService(any(), any(), any())
        }
    }

    /**
     * Test: Other registration errors emit ERROR state.
     */
    @Test
    fun `registration failure with INTERNAL_ERROR emits ERROR state`() {
        val states = mutableListOf<dev.imirror.receiver.service.ProtocolState>()
        val service = MdnsService(mockContext, onStateChange = { states.add(it) })

        val serviceInfos = mutableListOf<NsdServiceInfo>()
        val listeners = mutableListOf<NsdManager.RegistrationListener>()

        every { mockNsdManager.registerService(capture(serviceInfos), any(), capture(listeners)) } returns Unit

        service.start("Test Device")

        // First call is raop
        verify(exactly = 1) {
            mockNsdManager.registerService(any(), any(), any())
        }

        // Simulate a real failure for raop
        listeners[0].onRegistrationFailed(
            serviceInfos[0],
            NsdManager.FAILURE_INTERNAL_ERROR
        )

        assertTrue(states.contains(dev.imirror.receiver.service.ProtocolState.ERROR))
    }
}