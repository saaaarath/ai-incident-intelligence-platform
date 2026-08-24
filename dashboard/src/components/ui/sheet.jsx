import * as React from "react";
import { cn } from "../../lib/utils";
import { X } from "lucide-react";

export function Sheet({ open, onOpenChange, children }) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-black/70 backdrop-blur-xs transition-opacity animate-in fade-in"
        onClick={() => onOpenChange(false)}
      />
      {/* Drawer content */}
      <div className="relative z-50 h-full w-full max-w-2xl bg-gray-900 border-l border-gray-800 shadow-2xl overflow-y-auto flex flex-col transition-transform animate-in slide-in-from-right duration-200">
        {children}
      </div>
    </div>
  );
}

export function SheetHeader({ className, children, ...props }) {
  return (
    <div className={cn("p-6 border-b border-gray-800 flex items-start justify-between gap-4", className)} {...props}>
      {children}
    </div>
  );
}

export function SheetTitle({ className, children, ...props }) {
  return (
    <h2 className={cn("text-lg font-semibold text-gray-100", className)} {...props}>
      {children}
    </h2>
  );
}

export function SheetDescription({ className, children, ...props }) {
  return (
    <p className={cn("text-sm text-gray-400 mt-1", className)} {...props}>
      {children}
    </p>
  );
}

export function SheetContent({ className, children, ...props }) {
  return (
    <div className={cn("p-6 flex-1 flex flex-col gap-6", className)} {...props}>
      {children}
    </div>
  );
}

export function SheetFooter({ className, children, ...props }) {
  return (
    <div className={cn("p-6 border-t border-gray-800 flex items-center gap-3 mt-auto bg-gray-900/80 sticky bottom-0", className)} {...props}>
      {children}
    </div>
  );
}

export function SheetClose({ onClose }) {
  return (
    <button
      onClick={onClose}
      className="rounded-md p-1.5 text-gray-400 hover:text-gray-100 hover:bg-gray-800 transition-colors cursor-pointer"
    >
      <X className="h-5 w-5" />
      <span className="sr-only">Close</span>
    </button>
  );
}
