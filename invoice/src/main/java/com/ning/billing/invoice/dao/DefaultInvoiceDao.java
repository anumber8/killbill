/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationNotification;
import com.ning.billing.util.eventbus.Bus;

public class DefaultInvoiceDao implements InvoiceDao {
    private final InvoiceSqlDao invoiceSqlDao;
    private final InvoiceItemSqlDao invoiceItemSqlDao;

    private final Bus eventBus;

    @Inject
    public DefaultInvoiceDao(final IDBI dbi, final Bus eventBus) {
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        this.eventBus = eventBus;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final String accountId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
             @Override
             public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                 List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId);

                 InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                 for (final Invoice invoice : invoices) {
                     List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoice.getId().toString());
                     invoice.addInvoiceItems(invoiceItems);
                 }

                 InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                 for (final Invoice invoice : invoices) {
                     String invoiceId = invoice.getId().toString();
                     List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId);
                     invoice.addPayments(invoicePayments);
                 }

                 return invoices;
             }
        });
    }

    @Override
    public List<InvoiceItem> getInvoiceItemsByAccount(final String accountId) {
        return invoiceItemSqlDao.getInvoiceItemsByAccount(accountId);
    }

    @Override
    public List<Invoice> get() {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
             @Override
             public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                 List<Invoice> invoices = invoiceDao.get();

                 InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                 for (final Invoice invoice : invoices) {
                     List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoice.getId().toString());
                     invoice.addInvoiceItems(invoiceItems);
                 }

                 InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                 for (final Invoice invoice : invoices) {
                     String invoiceId = invoice.getId().toString();
                     List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId);
                     invoice.addPayments(invoicePayments);
                 }

                 return invoices;
             }
        });
    }

    @Override
    public Invoice getById(final String invoiceId) {
        return invoiceSqlDao.inTransaction(new Transaction<Invoice, InvoiceSqlDao>() {
             @Override
             public Invoice inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                 Invoice invoice = invoiceDao.getById(invoiceId);

                 if (invoice != null) {
                     InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                     List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoiceId);
                     invoice.addInvoiceItems(invoiceItems);

                     InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                     List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId);
                     invoice.addPayments(invoicePayments);
                 }

                 return invoice;
             }
        });
    }

    @Override
    public void create(final Invoice invoice) {
        invoiceSqlDao.inTransaction(new Transaction<Void, InvoiceSqlDao>() {
            @Override
            public Void inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                Invoice currentInvoice = invoiceDao.getById(invoice.getId().toString());

                if (currentInvoice == null) {
                    invoiceDao.create(invoice);

                    List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
                    InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                    invoiceItemDao.create(invoiceItems);

                    List<InvoicePayment> invoicePayments = invoice.getPayments();
                    InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                    invoicePaymentSqlDao.create(invoicePayments);

                    InvoiceCreationNotification event;
                    event = new DefaultInvoiceCreationNotification(invoice.getId(), invoice.getAccountId(),
                                                                  invoice.getBalance(), invoice.getCurrency(),
                                                                  invoice.getInvoiceDate());
                    eventBus.post(event);
                }

                return null;
            }
        });
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(final String subscriptionId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId);

                InvoiceItemSqlDao invoiceItemDao = invoiceDao.become(InvoiceItemSqlDao.class);
                for (final Invoice invoice : invoices) {
                    List<InvoiceItem> invoiceItems = invoiceItemDao.getInvoiceItemsByInvoice(invoice.getId().toString());
                    invoice.addInvoiceItems(invoiceItems);
                }

                InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                for (final Invoice invoice : invoices) {
                    String invoiceId = invoice.getId().toString();
                    List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId);
                    invoice.addPayments(invoicePayments);
                }

                return invoices;
            }
        });
    }

    @Override
    public List<UUID> getInvoicesForPayment(final Date targetDate, final int numberOfDays) {
        return invoiceSqlDao.getInvoicesForPayment(targetDate, numberOfDays);
    }

    @Override
    public void notifySuccessfulPayment(final String invoiceId, final BigDecimal paymentAmount,
                                        final String currency, final String paymentId, final Date paymentDate) {
        invoiceSqlDao.notifySuccessfulPayment(invoiceId, paymentAmount, currency, paymentId, paymentDate);
    }

    @Override
    public void notifyFailedPayment(final String invoiceId, final String paymentId, final Date paymentAttemptDate) {
        invoiceSqlDao.notifyFailedPayment(invoiceId, paymentId, paymentAttemptDate);
    }

    @Override
    public void test() {
        invoiceSqlDao.test();
    }
}
