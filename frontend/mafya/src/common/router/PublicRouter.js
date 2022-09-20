import React from "react";
import { Route, Redirect } from "react-router-dom";
//import { useSelector } from "react-redux";
import { isLogin } from "../api/isLogin";

export default function PublicRoute({
  component: Component,
  restricted,
  ...rest
}) {
  // const { isLoggedIn } = useSelector((state) => state.auth);

  return (
    <Route
      {...rest}
      render={(props) =>
        isLogin() && restricted ? (
          <Redirect to="/student" />
        ) : (
          <Component {...props} />
        )
      }
    />
  );
}
