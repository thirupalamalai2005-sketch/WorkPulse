package com.workpulse.repository;

import com.workpulse.entity.Employee;
import com.workpulse.entity.EmployeeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    Optional<Employee> findByUser_UserId(Long userId);
    Optional<Employee> findByUser_Email(String email);

    long countByStatus(EmployeeStatus status);
    long countByDepartment_Id(Long departmentId);
    boolean existsByManager_Id(Long managerId);
    List<Employee> findByStatus(EmployeeStatus status);
    List<Employee> findByManager_IdAndStatus(Long managerId, EmployeeStatus status);

    /** [departmentId, employeeCount] rows. */
    @Query("select e.department.id, count(e) from Employee e where e.department is not null group by e.department.id")
    List<Object[]> countGroupedByDepartment();

    List<Employee> findByDepartment_Id(Long departmentId);
}

