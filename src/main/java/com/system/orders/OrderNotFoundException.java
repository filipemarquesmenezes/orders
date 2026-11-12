package com.system.orders;

public class OrderNotFoundException extends RuntimeException {
	public OrderNotFoundException(long id) {
		super("order " + id + " was not found");
	}
}
