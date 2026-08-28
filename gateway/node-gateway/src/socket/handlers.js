const { socketAuthMiddleware, joinRoleRooms } = require('../middleware/auth');

/**
 * Attach the realtime namespace (/realtime) to an existing Socket.IO server.
 *
 * Lifecycle of a client connection:
 *   1. socketAuthMiddleware runs → JWT verified, socket.data populated
 *      (or rejected → client gets connect_error)
 *   2. on 'connection': joinRoleRooms puts the socket in admins / warehouse /
 *      user:<id> based on the role in the JWT
 *   3. on 'disconnect': rooms are cleaned up automatically by the server
 */
function registerRealtimeNamespace(io) {
    const realtime = io.of('/realtime');

    realtime.use(socketAuthMiddleware);

    realtime.on('connection', (socket) => {
        const { userId, role } = socket.data;
        console.log(`[realtime] connected userId=${userId} role=${role} sid=${socket.id}`);

        joinRoleRooms(socket);

        // Optional: client can ask which rooms it currently belongs to.
        socket.on('whoami', (cb) => {
            if (typeof cb === 'function') {
                cb({
                    userId: socket.data.userId,
                    role: socket.data.role,
                    rooms: Array.from(socket.rooms),
                });
            }
        });

        socket.on('disconnect', (reason) => {
            console.log(`[realtime] disconnected userId=${userId} sid=${socket.id} reason=${reason}`);
        });
    });

    return realtime;
}

module.exports = { registerRealtimeNamespace };