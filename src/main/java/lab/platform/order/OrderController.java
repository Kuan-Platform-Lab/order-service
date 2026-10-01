package lab.platform.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/orders")
class OrderController {

	private static final Logger log = LoggerFactory.getLogger(OrderController.class);

	record OrderRequest(String customerId, BigDecimal amount) {}
	record Order(long id, String customerId, BigDecimal amount, String status) {}

	private final JdbcClient jdbc;
	private final RestClient payment;

	OrderController(JdbcClient jdbc, RestClient.Builder builder,
			@Value("${payment.base-url}") String paymentUrl) {
		this.jdbc = jdbc;
		this.payment = builder.baseUrl(paymentUrl).build();
	}

	@GetMapping
	List<Order> list() {
		return jdbc.sql("select id, customer_id, amount, status from orders order by id desc limit 50")
				.query((rs, i) -> new Order(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4)))
				.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Order create(@RequestBody OrderRequest req) {
		long id = jdbc.sql("insert into orders(customer_id, amount, status) values (?, ?, 'PENDING') returning id")
				.params(req.customerId(), req.amount()).query(Long.class).single();
		log.info("order created id={} customer={}", id, req.customerId());

		Map<?, ?> result = payment.post().uri("/payments").body(Map.of("orderId", id, "amount", req.amount()))
				.retrieve().body(Map.class);
		String status = "APPROVED".equals(result.get("status")) ? "PAID" : "PAYMENT_FAILED";
		jdbc.sql("update orders set status = ? where id = ?").params(status, id).update();
		log.info("order id={} status={}", id, status);
		return new Order(id, req.customerId(), req.amount(), status);
	}
}
