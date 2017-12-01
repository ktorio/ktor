var socket = null;

function connect() {
    console.log("Begin connect");
    socket = new WebSocket("ws://" + window.location.host + "/ws");

    socket.onerror = function() {
        console.log("socket error");
    };

    socket.onopen = function() {
        write("Connected");
    };

    socket.onclose = function(evt) {
        var explanation = "";
        if (evt.reason && evt.reason.length > 0) {
            explanation = "reason: " + evt.reason;
        } else {
            explanation = "without a reason specified";
        }

        write("Disconnected with close code " + evt.code + " and " + explanation);
        setTimeout(connect, 5000);
    };

    socket.onmessage = function(event) {
        received(event.data.toString());
    };
}

function received(message) {
    write(message);
}

function write(message) {
    var line = document.createElement("p");
    line.className = "message";
    line.textContent = message;

    var messagesDiv = document.getElementById("messages");
    messagesDiv.appendChild(line);
    messagesDiv.scrollTop = line.offsetTop;
}

function onSend() {
    var input = document.getElementById("commandInput");
    if (input) {
        var text = input.value;
        if (text && socket) {
            socket.send(text);
            input.value = "";
        }
    }
}

function start() {
    connect();

    document.getElementById("sendButton").onclick = onSend;
    document.getElementById("commandInput").onkeydown = function(e) {
        if (e.keyCode == 13) {
            onSend();
        }
    };
}

function initLoop() {
    if (document.getElementById("sendButton")) {
        start();
    } else {
        setTimeout(initLoop, 300);
    }
}

initLoop();
