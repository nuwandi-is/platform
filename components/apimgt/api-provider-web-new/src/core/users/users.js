
function getUser() {

    return {
        username:session.get("username"),
        cookie:session.get("cookie")
    };

}

