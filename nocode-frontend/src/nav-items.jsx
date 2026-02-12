import { HomeIcon, LogIn, Music } from "lucide-react";
import Index from "./pages/Index.jsx";
import Login from "./pages/Login.jsx";
import Home from "./pages/Home.jsx";
import Player from "./pages/Player.jsx";

/**
 * Central place for defining the navigation items. Used for navigation components and routing.
 */
export const navItems = [
  {
    title: "首页",
    to: "/",
    icon: <HomeIcon className="h-4 w-4" />,
    page: <Home />,
  },
  {
    title: "登录",
    to: "/login",
    icon: <LogIn className="h-4 w-4" />,
    page: <Login />,
  },
  {
    title: "音乐图谱",
    to: "/player/:id",
    icon: <Music className="h-4 w-4" />,
    page: <Player />,
  },
];
