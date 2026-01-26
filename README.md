# Bodindecha 2 Electives

Fast and student-friendly electives management system for [Bodindecha (Sing Singhaseni) 2 School](https://bodin2.ac.th).

## Why?

This project aims to improve the existing Electives system at [Bodindecha (Sing Singhaseni) 2 School](http://bodin2.ac.th).
Providing a fast and student-friendly interface for students to view available subjects, see their details, and make selections.

### Issues with the previous system

The previous system incredibly slow, being server-rendered by PHP on a mid-end server. This caused frequent timeouts and freezes when thousands of students tried to access the system at the same time.  
There are also other issues, such as students not being to view details of subjects before the registration period, or overbooking, needing manual intervention from teachers to resolve such conflicts, or UX issues like students needing to refresh to view latest information, or needing to input credentials during high server load.

### How are we solving it?

#### Backend

- Using [Ktor](https://ktor.io) allows us to ergonomically create a server that can handle high loads of traffic in a short amount of time.
- [Protocol Buffers](https://protobuf.dev) provide a compact binary format for data exchange, making it efficient to send and receive data between the server and clients.
- [SQLite](https://sqlite.org) allows us to manage data efficiently, ensuring that data is stored and retrieved quickly.  
  We are using [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) with [JetBrains Exposed](https://www.jetbrains.com/exposed), which binds fully-typed database schemas and data-access-objects to a high-performance native SQLite3 binding.
- WebSockets allow students and teachers to get up-to-date information without needing to refresh.

#### Frontend

- Built with [SolidStart](https://start.solidjs.com), fully client-sided. We provide a small-sized, responsive, and interactive user interface for students to view and select subjects easier and faster.  
  Being fully client-sided also means the frontend can be fully cached through services like Cloudflare to prevent downtime from too many requests hitting the server at once!  
  Serving content manually was one of the bottlenecks with the previous system, so we intend to serve the frontend through Cloudflare instead, which should fix slow loads on clients.

## Security policy

Please refer to [SECURITY.md](./SECURITY.md).

## Contributing

Please refer to [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

This project is licensed under [CC BY-NC-SA 4.0](./LICENSE).

Graphic assets (PNG, JPG, WEBP, ICO, etc.) in this project have all rights reserved by their respective owners.
