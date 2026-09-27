package com.orionkey.repository;

import com.orionkey.entity.SupportImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupportImageRepository extends JpaRepository<SupportImage, UUID> {
}
