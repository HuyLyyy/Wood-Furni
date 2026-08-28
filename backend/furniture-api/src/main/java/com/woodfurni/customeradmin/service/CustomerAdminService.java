package com.woodfurni.customeradmin.service;

import com.woodfurni.auth.enums.Role;
import com.woodfurni.auth.enums.UserStatus;
import com.woodfurni.auth.model.User;
import com.woodfurni.auth.repository.UserRepository;
import com.woodfurni.common.EntityNotFoundException;
import com.woodfurni.customeradmin.dto.CustomerAdminView;
import com.woodfurni.customeradmin.dto.CustomerDetailView;
import com.woodfurni.customeradmin.dto.CustomerOrderSummary;
import com.woodfurni.order.enums.PaymentStatus;
import com.woodfurni.order.model.Order;
import com.woodfurni.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Customer (admin) queries. All operations are read-only and admin/SALES
 * scoped at the controller layer.
 */
@Service
@RequiredArgsConstructor
public class CustomerAdminService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * List CUSTOMER-role users with their orderCount + totalSpent.
     *
     * Implementation note: we deliberately DO NOT use an aggregation pipeline
     * for the per-row totals — instead we make a single bulk query into
     * the orders collection grouped by customerId. For the dataset size in
     * this thesis project (a few thousand orders max) this is plenty fast
     * and avoids the maintenance cost of aggregation pipelines.
     */
    public List<CustomerAdminView> listCustomers(int page, int size) {
        Query userQuery = new Query()
                .addCriteria(Criteria.where("role").is(Role.CUSTOMER))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        List<User> users = mongoTemplate.find(userQuery, User.class);
        if (users.isEmpty()) return Collections.emptyList();

        Set<String> ids = users.stream().map(User::getId).collect(Collectors.toSet());
        OrderCountByCustomer totals = aggregateOrderTotals(ids);

        return users.stream()
                .map(u -> toView(u, totals))
                .collect(Collectors.toList());
    }

    /**
     * Count CUSTOMER-role users — used to drive the list pagination.
     */
    public long countCustomers() {
        Query q = new Query().addCriteria(Criteria.where("role").is(Role.CUSTOMER));
        return mongoTemplate.count(q, User.class);
    }

    /**
     * Detail view of one customer + their full order history.
     */
    public CustomerDetailView getCustomerDetail(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + id));

        if (user.getRole() != Role.CUSTOMER) {
            // Guard: customer-detail is meant for shoppers, not staff.
            throw new IllegalArgumentException(
                    "User " + id + " is not a CUSTOMER (role=" + user.getRole() + ")");
        }

        List<Order> orders = orderRepository.findByCustomerId(
                id,
                PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        OrderCountByCustomer totals = aggregateOrderTotals(Set.of(id));
        CustomerAdminView base = toView(user, totals);

        List<CustomerOrderSummary> summaries = orders.stream()
                .map(this::toOrderSummary)
                .collect(Collectors.toList());

        return CustomerDetailView.builder()
                .id(base.getId())
                .email(base.getEmail())
                .fullName(base.getFullName())
                .phone(base.getPhone())
                .role(base.getRole())
                .status(base.getStatus())
                .orderCount(base.getOrderCount())
                .totalSpent(base.getTotalSpent())
                .orders(summaries)
                .build();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private CustomerAdminView toView(User u, OrderCountByCustomer totals) {
        OrderCountByCustomer.Entry e = totals.byUserId.getOrDefault(
                u.getId(),
                new OrderCountByCustomer.Entry(0, BigDecimal.ZERO));
        return CustomerAdminView.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .role(u.getRole())
                .status(u.getStatus() == null ? UserStatus.ACTIVE : u.getStatus())
                .createdAt(u.getCreatedAt())
                .orderCount(e.count)
                .totalSpent(e.spent)
                .build();
    }

    private CustomerOrderSummary toOrderSummary(Order o) {
        int itemCount = o.getItems() == null ? 0 : o.getItems().size();
        return CustomerOrderSummary.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .status(o.getStatus())
                .paymentStatus(o.getPaymentStatus())
                .totalAmount(o.getTotalAmount())
                .createdAt(o.getCreatedAt())
                .itemCount(itemCount)
                .build();
    }

    /**
     * Returns a map of customerId → (orderCount, totalSpent).
     * Only counts PAID orders toward totalSpent — that's what the
     * business actually cares about for "VIP" ranking.
     */
    private OrderCountByCustomer aggregateOrderTotals(Set<String> userIds) {
        if (userIds.isEmpty()) return new OrderCountByCustomer();
        Query q = new Query().addCriteria(
                Criteria.where("customerId").in(userIds)
                        .and("paymentStatus").is(PaymentStatus.PAID));
        q.fields().include("customerId").include("totalAmount");
        List<Order> rows = mongoTemplate.find(q, Order.class);

        OrderCountByCustomer result = new OrderCountByCustomer();
        // Total order count (regardless of payment status) — separate query.
        Query allQ = new Query().addCriteria(Criteria.where("customerId").in(userIds));
        allQ.fields().include("customerId");
        List<Order> allRows = mongoTemplate.find(allQ, Order.class);
        for (Order o : allRows) {
            result.addCount(o.getCustomerId());
        }
        for (Order o : rows) {
            BigDecimal amount = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
            result.addSpent(o.getCustomerId(), amount);
        }
        return result;
    }

    /**
     * Local helper holding the per-customer metric results so we can
     * pass both fields between helpers without juggling two maps.
     */
    static final class OrderCountByCustomer {
        static final class Entry {
            long count;
            BigDecimal spent;
            Entry(long c, BigDecimal s) { this.count = c; this.spent = s; }
        }
        private final java.util.Map<String, Entry> byUserId = new java.util.HashMap<>();
        void addCount(String id) {
            Entry e = byUserId.computeIfAbsent(id, k -> new Entry(0, BigDecimal.ZERO));
            e.count++;
        }
        void addSpent(String id, BigDecimal amount) {
            Entry e = byUserId.computeIfAbsent(id, k -> new Entry(0, BigDecimal.ZERO));
            e.spent = e.spent.add(amount == null ? BigDecimal.ZERO : amount);
        }
    }
}
