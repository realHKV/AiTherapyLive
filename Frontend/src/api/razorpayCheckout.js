const CHECKOUT_URL = 'https://checkout.razorpay.com/v1/checkout.js';
let scriptPromise;

export function loadRazorpayCheckout() {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = CHECKOUT_URL;
      script.async = true;
      script.onload = () => window.Razorpay
        ? resolve(window.Razorpay)
        : reject(new Error('Payment checkout is unavailable. Please try again.'));
      script.onerror = () => reject(new Error('Payment checkout could not load. Please try again.'));
      document.head.appendChild(script);
    }).catch(error => {
      scriptPromise = null;
      throw error;
    });
  }
  return scriptPromise;
}

export function openRazorpayCheckout(Razorpay, purchase, onPayment) {
  if (!purchase?.orderId || !purchase?.keyId || !Number.isSafeInteger(purchase.amountPaise)
      || purchase.amountPaise <= 0 || purchase.currency !== 'INR') {
    throw new Error('Invalid payment order. Please try again.');
  }
  const checkout = new Razorpay({
    key: purchase.keyId,
    order_id: purchase.orderId,
    amount: purchase.amountPaise,
    currency: purchase.currency,
    name: 'TrustTherapy.ai',
    description: '30 days of Pro access',
    handler: response => onPayment(response.razorpay_payment_id),
  });
  checkout.open();
}
