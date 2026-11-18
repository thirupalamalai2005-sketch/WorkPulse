package com.workpulse.integration;

import com.workpulse.entity.Employee;
import com.workpulse.entity.LeaveRequest;
import com.workpulse.entity.LeaveStatus;
import com.workpulse.entity.Role;
import com.workpulse.repository.LeaveRequestRepository;
import com.workpulse.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaveConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    private Map<String, Object> leave(String code, String from, String to) {
        Map<String, Object> m = new HashMap<>();
        m.put("leaveTypeId", leaveTypeId(code));
        m.put("startDate", from);
        m.put("endDate", to);
        m.put("reason", "Concurrency Test");
        return m;
    }

    private long submit(Employee e, String code, String from, String to) throws Exception {
        return om.readTree(doPost("/api/leaves", leave(code, from, to), as(e)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void concurrentOverlappingSubmits_oneSucceedsOneConflicts() throws Exception {
        Employee e = newUser("concurrent.overlap@wp.com", Role.EMPLOYEE);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Thread 1: March 9 - March 11 (3 days)
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves", leave("ANNUAL", "2026-03-09", "2026-03-11"), as(e)).andReturn();
                int sc = res.getResponse().getStatus();
                if (sc == 201) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception ex) {
                // handled by assert
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: March 10 - March 12 (3 days, overlaps March 10 & 11)
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves", leave("ANNUAL", "2026-03-10", "2026-03-12"), as(e)).andReturn();
                int sc = res.getResponse().getStatus();
                if (sc == 201) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception ex) {
                // handled by assert
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent submits must finish within timeout");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one submit must succeed (201 Created)");
        assertEquals(1, conflictCount.get(), "The overlapping submit must receive 409 Conflict");

        List<LeaveRequest> list = leaveRequestRepository.findAll();
        assertEquals(1, list.size(), "Only one request must exist in database");
    }

    @Test
    void concurrentBalanceOverdraw_oneSucceedsOneRejected() throws Exception {
        // Casual leave has 6 days entitlement.
        Employee e = newUser("concurrent.balance@wp.com", Role.EMPLOYEE);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger badRequestCount = new AtomicInteger(0);

        // Thread 1: 4 days (March 9-12)
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves", leave("CASUAL", "2026-03-09", "2026-03-12"), as(e)).andReturn();
                int sc = res.getResponse().getStatus();
                if (sc == 201) successCount.incrementAndGet();
                else if (sc == 400 || sc == 409) badRequestCount.incrementAndGet();
            } catch (Exception ex) {
                // handled by assert
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: 4 days (March 16-19, non-overlapping dates)
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves", leave("CASUAL", "2026-03-16", "2026-03-19"), as(e)).andReturn();
                int sc = res.getResponse().getStatus();
                if (sc == 201) successCount.incrementAndGet();
                else if (sc == 400 || sc == 409) badRequestCount.incrementAndGet();
            } catch (Exception ex) {
                // handled by assert
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent balance test must finish within timeout");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one 4-day submit must succeed");
        assertEquals(1, badRequestCount.get(), "The second submit must be rejected due to insufficient balance");

        List<LeaveRequest> list = leaveRequestRepository.findAll();
        assertEquals(1, list.size(), "Only one request must exist in database");
        assertEquals(4, list.get(0).getDayCount());
    }

    @Test
    void concurrentDoubleApprove_oneSucceedsOneConflicts() throws Exception {
        Employee manager = newUser("mgr.double@wp.com", Role.MANAGER);
        Employee admin = newUser("admin.double@wp.com", Role.ADMIN);
        Employee e = newUser("emp.double@wp.com", Role.EMPLOYEE, manager);
        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-10");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves/" + id + "/approve", Map.of("note", "Manager approves"), as(manager)).andReturn();
                int sc = res.getResponse().getStatus();
                if (sc == 200) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception ex) {
                // handled by assert
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves/" + id + "/approve", Map.of("note", "Admin approves"), as(admin)).andReturn();
                int sc = res.getResponse().getStatus();
                if (sc == 200) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception ex) {
                // handled by assert
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent approvals must finish within timeout");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one approval must succeed");
        assertEquals(1, conflictCount.get(), "The concurrent approval must receive 409 Conflict");

        LeaveRequest lr = leaveRequestRepository.findById(id).orElseThrow();
        assertEquals(LeaveStatus.APPROVED, lr.getStatus());
    }

    @Test
    void concurrentApproveVsReject_oneSucceedsOneConflicts() throws Exception {
        Employee manager = newUser("mgr.apprej@wp.com", Role.MANAGER);
        Employee admin = newUser("admin.apprej@wp.com", Role.ADMIN);
        Employee e = newUser("emp.apprej@wp.com", Role.EMPLOYEE, manager);
        long id = submit(e, "ANNUAL", "2026-03-09", "2026-03-10");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves/" + id + "/approve", Map.of("note", "Approved"), as(manager)).andReturn();
                int sc = res.getResponse().getStatus();
                if (sc == 200) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception ex) {
                // handled by assert
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves/" + id + "/reject", Map.of("note", "Rejected"), as(admin)).andReturn();
                int sc = res.getResponse().getStatus();
                if (sc == 200) successCount.incrementAndGet();
                else if (sc == 409) conflictCount.incrementAndGet();
            } catch (Exception ex) {
                // handled by assert
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one decision must succeed");
        assertEquals(1, conflictCount.get(), "The concurrent decision must receive 409 Conflict");

        LeaveRequest lr = leaveRequestRepository.findById(id).orElseThrow();
        assertTrue(lr.getStatus() == LeaveStatus.APPROVED || lr.getStatus() == LeaveStatus.REJECTED);
    }

    @Test
    void concurrentCancelVsApprove_handledDeterministically() throws Exception {
        Employee manager = newUser("mgr.canapp@wp.com", Role.MANAGER);
        Employee e = newUser("emp.canapp@wp.com", Role.EMPLOYEE, manager);
        long id = submit(e, "ANNUAL", "2026-03-16", "2026-03-17");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                startLatch.await();
                doPost("/api/leaves/" + id + "/approve", Map.of("note", "Approved"), as(manager));
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                doPost("/api/leaves/" + id + "/cancel", null, as(e));
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        LeaveRequest lr = leaveRequestRepository.findById(id).orElseThrow();
        // Since request is for upcoming dates (March 16-17), owner can cancel pending or upcoming approved.
        // Final state is either APPROVED or CANCELLED, never in an invalid state.
        assertTrue(lr.getStatus() == LeaveStatus.APPROVED || lr.getStatus() == LeaveStatus.CANCELLED);
    }

    @Test
    void failedSubmitLeavesNoRowAndNoNotification() throws Exception {
        Employee manager = newUser("mgr.failsub@wp.com", Role.MANAGER);
        Employee e = newUser("emp.failsub@wp.com", Role.EMPLOYEE, manager);

        // Submitting with days exceeding balance (CASUAL has 6 days entitlement; request 8 days)
        doPost("/api/leaves", leave("CASUAL", "2026-03-09", "2026-03-18"), as(e))
                .andExpect(status().isBadRequest());

        // Submitting with inverted dates
        doPost("/api/leaves", leave("ANNUAL", "2026-03-15", "2026-03-10"), as(e))
                .andExpect(status().isBadRequest());

        // Submitting invalid leave type
        Map<String, Object> invalidType = Map.of("leaveTypeId", 99999L, "startDate", "2026-03-09", "endDate", "2026-03-10", "reason", "Invalid");
        doPost("/api/leaves", invalidType, as(e)).andExpect(status().isBadRequest());

        // Assert final database state: 0 leave requests and 0 notifications
        assertEquals(0, leaveRequestRepository.count(), "Failed submit must not leave any row in leave_requests");
        Long notifCount = jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);
        assertEquals(0L, notifCount, "Failed submit must not leave any notification");
    }

    @Test
    void independentEmployeesSubmitConcurrently_bothSucceed() throws Exception {
        Employee e1 = newUser("emp.ind1@wp.com", Role.EMPLOYEE);
        Employee e2 = newUser("emp.ind2@wp.com", Role.EMPLOYEE);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves", leave("ANNUAL", "2026-03-09", "2026-03-11"), as(e1)).andReturn();
                if (res.getResponse().getStatus() == 201) successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = doPost("/api/leaves", leave("ANNUAL", "2026-03-09", "2026-03-11"), as(e2)).andReturn();
                if (res.getResponse().getStatus() == 201) successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(2, successCount.get(), "Both independent employee submits must succeed concurrently");
        assertEquals(2, leaveRequestRepository.findAll().size());
    }
}
