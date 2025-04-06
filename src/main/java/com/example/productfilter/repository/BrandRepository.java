package com.example.productfilter.repository;

import com.example.productfilter.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface BrandRepository extends JpaRepository<Brand, Integer> {}