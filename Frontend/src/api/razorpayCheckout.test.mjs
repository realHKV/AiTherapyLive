import assert from 'node:assert/strict';
import test from 'node:test';
import { openRazorpayCheckout } from './razorpayCheckout.js';

test('checkout uses only server purchase details and forwards payment ID', () => {
  let options;
  let opened = false;
  let receivedPaymentId;
  class FakeRazorpay {
    constructor(value) { options = value; }
    open() { opened = true; }
  }
  openRazorpayCheckout(FakeRazorpay, {
    purchaseId: 'purchase', orderId: 'order_123', keyId: 'rzp_test_123',
    amountPaise: 5000, currency: 'INR',
  }, paymentId => { receivedPaymentId = paymentId; });

  assert.equal(opened, true);
  assert.equal(options.order_id, 'order_123');
  assert.equal(options.key, 'rzp_test_123');
  assert.equal(options.amount, 5000);
  options.handler({ razorpay_payment_id: 'pay_123456' });
  assert.equal(receivedPaymentId, 'pay_123456');
});

test('checkout refuses an incomplete or invalid server order', () => {
  class FakeRazorpay {
    constructor() { throw new Error('Should not open checkout'); }
  }
  assert.throws(() => openRazorpayCheckout(FakeRazorpay, {
    orderId: 'order_123', keyId: 'rzp_test_123', amountPaise: 0, currency: 'INR',
  }, () => {}), /Invalid payment order/);
});
