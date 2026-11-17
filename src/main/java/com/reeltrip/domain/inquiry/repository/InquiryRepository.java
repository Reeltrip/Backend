package com.reeltrip.domain.inquiry.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.inquiry.entity.Inquiry;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> { }
