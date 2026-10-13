-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
-- 仅限个人学习、技术研究与非商业交流；商用须书面授权。
create table command_stamp(id bigint auto_increment primary key,request_id varchar(36) not null unique,actor_id bigint not null,fingerprint varchar(64) not null,result_type varchar(30) not null,result_id bigint not null,foreign key(actor_id) references account(id));
create table customer (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
name varchar(120) not null,
contact varchar(120) not null,
account_id bigint,
billing varchar(20) not null,
currency varchar(3) not null,
credit_limit bigint not null,
enabled boolean not null,
foreign key(department_id) references department(id),
foreign key(account_id) references account(id),
unique(account_id));

create table monthly_invoice (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
number varchar(60) not null,
customer_id bigint not null,
customer_name varchar(120) not null,
billing_month varchar(7) not null,
currency varchar(3) not null,
total bigint not null,
created_at timestamp(6) not null,
state varchar(20) not null,
foreign key(department_id) references department(id),
foreign key(customer_id) references customer(id),
unique(number));

create table wash_service (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
name varchar(120) not null,
name_en varchar(120) not null,
category varchar(60) not null,
currency varchar(3) not null,
price bigint not null,
enabled boolean not null,
foreign key(department_id) references department(id));

create table rack (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
code varchar(60) not null,
name varchar(120) not null,
capacity int not null,
enabled boolean not null,
foreign key(department_id) references department(id),
unique(department_id,code));

create table wash_order (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
number varchar(60) not null,
customer_id bigint not null,
customer_name varchar(120) not null,
currency varchar(3) not null,
billing varchar(20) not null,
due_date date not null,
state varchar(20) not null,
total bigint not null,
note varchar(2000) not null,
created_at timestamp(6) not null,
completed_at timestamp(6),
invoice_id bigint,
foreign key(department_id) references department(id),
foreign key(customer_id) references customer(id),
foreign key(invoice_id) references monthly_invoice(id),
unique(number));

create table garment (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
order_id bigint not null,
barcode varchar(60) not null,
description varchar(200) not null,
condition_note varchar(1000) not null,
service_name varchar(120) not null,
service_name_en varchar(120) not null,
price bigint not null,
state varchar(20) not null,
rack_id bigint,
rewash_count int not null,
delivered_at timestamp(6),
foreign key(department_id) references department(id),
foreign key(order_id) references wash_order(id),
foreign key(rack_id) references rack(id),
unique(barcode));

create table pickup (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
order_id bigint not null,
receiver varchar(120) not null,
actor varchar(60) not null,
note varchar(1000) not null,
created_at timestamp(6) not null,
foreign key(department_id) references department(id),
foreign key(order_id) references wash_order(id));


create table pickup_item (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
pickup_id bigint not null,
garment_id bigint not null,
barcode varchar(60) not null,
foreign key(department_id) references department(id),
foreign key(garment_id) references garment(id),
foreign key(pickup_id) references pickup(id),
unique(garment_id));

create table ledger_entry (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
target_type varchar(20) not null,
target_id bigint not null,
original_id bigint,
kind varchar(20) not null,
amount bigint not null,
currency varchar(3) not null,
reference varchar(120) not null,
method varchar(30) not null,
actor varchar(60) not null,
created_at timestamp(6) not null,
foreign key(department_id) references department(id),
foreign key(original_id) references ledger_entry(id),
unique(reference));

create table garment_photo (id bigint auto_increment primary key,
department_id bigint not null,
version bigint not null,
garment_id bigint not null,
media_type varchar(30) not null,
created_at timestamp(6) not null,
data longblob not null,
foreign key(department_id) references department(id),
foreign key(garment_id) references garment(id));


create table business_event(id bigint auto_increment primary key, department_id bigint not null, version bigint not null,object_type varchar(30) not null,object_id bigint not null,action varchar(40) not null,actor varchar(60) not null,note varchar(2000) not null,created_at timestamp(6) not null,foreign key(department_id) references department(id));
create index idx_order_customer on wash_order(customer_id,created_at);
create index idx_garment_order on garment(order_id,state);
create index idx_order_department on wash_order(department_id,state);
