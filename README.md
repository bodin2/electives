# Bodindecha 2 Electives

A system for managing elective subjects at [Bodindecha (Sing Singhaseni) 2 School](https://bodin2.ac.th).

## Why?

This project aims to improve the existing Electives system at [Bodindecha (Sing Singhaseni) 2 School](http://bodin2.ac.th).
Providing a fast and user-friendly interface for students to view available subjects, see their details, and make selections.

### Issues with the previous system

The previous system was very slow, being server-rendered by PHP on a mid-to-low-end server. This caused frequent timeouts and freezes when thousands of students tried to access the system at the same time.  
There are also other issues, such as students not being to view details of subjects before the registration period, or subjects having overflowing student counts, needing manual intervention by teachers to resolve such conflicts.

### How are we solving it?

We built a new system using [ElysiaJS](https://elysiajs.com), [Protocol Buffers](https://protobuf.dev), and [Drizzle ORM](https://orm.drizzle.team) with [SQLite](https://sqlite.org), with a fully client-sided frontend built with [SolidStart](https://start.solidjs.com).

#### Backend

- Using [ElysiaJS](https://elysiajs.com) allows us to create a fast and lightweight server that can handle high loads of traffic in a short amount of time.
- [Protocol Buffers](https://protobuf.dev) provide a compact binary format for data exchange, making it efficient to send and receive data between the server and clients.
- [SQLite](https://sqlite.org) allows us to manage data efficiently, ensuring that data is stored and retrieved quickly.  
  We are using [Bun SQLite](https://bun.sh/docs/api/sqlite) with [Drizzle ORM](https://orm.drizzle.team), which binds fully-typed database schemas to Bun's high-performance native SQLite3 driver.
- Running it all with [Bun](https://bun.sh), a fast all-in-one JavaScript runtime & toolkit, allows us to run the server with very high performance, suitable for handling hundreds of requests per second.

#### Frontend

- Built with [SolidStart](https://start.solidjs.com), fully client-sided. We provide a small-sized, responsive, and interactive user interface for students to view and select subjects easier and faster.  
  Being fully client-sided also means the frontend can be cached through services like Cloudflare to prevent downtime from too many requests hitting the server at once!  
  Serving a lot of content was one of the issues with the previous system, so we are now serving it through Cloudflare instead, which is fast and reliable.

## License

This project is licensed under [CC BY-NC-SA 4.0](./LICENSE).
