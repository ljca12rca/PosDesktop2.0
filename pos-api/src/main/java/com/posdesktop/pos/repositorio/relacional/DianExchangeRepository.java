package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.DianExchange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DianExchangeRepository extends JpaRepository<DianExchange,UUID>{List<DianExchange> findBySubmissionIdOrderByCreatedAtAsc(UUID id);}
