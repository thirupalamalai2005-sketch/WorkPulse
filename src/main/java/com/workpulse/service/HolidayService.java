package com.workpulse.service;

import com.workpulse.dto.OrgDtos;
import com.workpulse.entity.Holiday;
import com.workpulse.exception.ConflictException;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class HolidayService {

    private final HolidayRepository repository;
    private final AuditService audit;

    public HolidayService(HolidayRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrgDtos.HolidayResponse> listForYear(int year) {
        return repository.findByHolidayDateBetweenOrderByHolidayDate(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().map(HolidayService::toResponse).toList();
    }

    @Transactional
    public OrgDtos.HolidayResponse create(OrgDtos.HolidayRequest req) {
        if (repository.existsByHolidayDate(req.date())) {
            throw new ConflictException("A holiday already exists on " + req.date());
        }
        Holiday h = new Holiday();
        h.setHolidayDate(req.date());
        h.setName(req.name().trim());
        h = repository.save(h);
        audit.logCurrent("HOLIDAY_CREATED", "HOLIDAY", h.getId(), req.date() + " " + h.getName());
        return toResponse(h);
    }

    @Transactional
    public void delete(Long id) {
        Holiday h = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
        repository.delete(h);
        audit.logCurrent("HOLIDAY_DELETED", "HOLIDAY", id, h.getHolidayDate() + " " + h.getName());
    }

    private static OrgDtos.HolidayResponse toResponse(Holiday h) {
        return new OrgDtos.HolidayResponse(h.getId(), h.getHolidayDate(), h.getName());
    }
}
